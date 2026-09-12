package dev.besan.browserbrake

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationManager
import android.util.Base64
import dev.besan.browserbrake.rules.RuleRepository
import dev.besan.browserbrake.rules.TargetGroupCatalog
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream

object FlutterCatalogBridge {
    private const val CHANNEL = "dev.besan.browserbrake/catalog"

    fun configure(activity: FlutterActivity, engine: FlutterEngine) {
        MethodChannel(engine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "getLaunchableApps" -> result.success(launchableApps(activity))
                    "getPlaces" -> result.success(places(activity))
                    "getCurrentLocation" -> result.success(currentLocation(activity))
                    "requestLocationPermission" -> {
                        activity.requestPermissions(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            4901
                        )
                        result.success(null)
                    }
                    "addPlace" -> {
                        val name = call.argument<String>("name").orEmpty()
                        val lat = call.argument<Number>("lat")?.toDouble() ?: 0.0
                        val lon = call.argument<Number>("lon")?.toDouble() ?: 0.0
                        val radius = call.argument<Number>("radiusM")?.toFloat() ?: 250f
                        result.success(addPlace(activity, name, lat, lon, radius))
                    }
                    "deletePlace" -> {
                        val id = call.argument<String>("id").orEmpty()
                        val usedBy = if (id.isBlank()) emptyList() else
                            RuleRepository.getRules(activity).filter { id in it.placeIds }.map { it.name }
                        if (id.isBlank()) {
                            result.success(mapOf("deleted" to false, "usedBy" to emptyList<String>()))
                        } else if (usedBy.isNotEmpty()) {
                            result.success(mapOf("deleted" to false, "usedBy" to usedBy))
                        } else {
                            PlaceStore.delete(activity, id)
                            result.success(mapOf("deleted" to true, "usedBy" to emptyList<String>()))
                        }
                    }
                    else -> result.notImplemented()
                }
            } catch (t: Throwable) {
                result.error("catalog_error", t.message, null)
            }
        }
    }

    private fun launchableApps(context: Context): List<Map<String, Any?>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val browserPackages = TargetApps.browserPackages(context)
        return pm.queryIntentActivities(intent, 0).asSequence().mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val pkg = activity.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            val icon = runCatching { drawablePngBase64(info.loadIcon(pm)) }.getOrNull()
            val category = appCategory(
                pkg = pkg,
                applicationCategory = activity.applicationInfo.category,
                browserPackages = browserPackages
            )
            mapOf(
                "package" to pkg,
                "label" to label,
                "icon" to icon,
                "category" to category
            )
        }.distinctBy { it["package"] }
            .sortedBy { (it["label"] as? String)?.lowercase() }
            .toList()
    }

    private fun appCategory(
        pkg: String,
        applicationCategory: Int,
        browserPackages: Set<String>
    ): String = when {
        TargetGroupCatalog.isSnsPackage(pkg) -> "social"
        pkg in browserPackages -> "browser"
        applicationCategory == ApplicationInfo.CATEGORY_SOCIAL -> "social"
        applicationCategory == ApplicationInfo.CATEGORY_VIDEO -> "video"
        applicationCategory == ApplicationInfo.CATEGORY_GAME -> "game"
        applicationCategory == ApplicationInfo.CATEGORY_AUDIO -> "audio"
        applicationCategory == ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
        applicationCategory == ApplicationInfo.CATEGORY_NEWS -> "news"
        applicationCategory == ApplicationInfo.CATEGORY_MAPS -> "maps"
        applicationCategory == ApplicationInfo.CATEGORY_IMAGE -> "image"
        else -> "other"
    }

    private fun places(context: Context): List<Map<String, Any?>> = PlaceStore.all(context).map {
        mapOf(
            "id" to it.id,
            "name" to it.name,
            "lat" to it.lat,
            "lon" to it.lon,
            "radiusM" to it.radiusM.toDouble()
        )
    }

    private fun addPlace(
        context: Context,
        name: String,
        lat: Double,
        lon: Double,
        radiusM: Float
    ): Map<String, Any?> {
        val place = PlaceStore.add(context, name.ifBlank { "場所" }, lat, lon, radiusM)
        return mapOf(
            "id" to place.id,
            "name" to place.name,
            "lat" to place.lat,
            "lon" to place.lon,
            "radiusM" to place.radiusM.toDouble()
        )
    }

    private fun currentLocation(context: Context): Map<String, Any?>? {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val best: Location = manager.getProviders(true).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time } ?: return null
        return mapOf(
            "lat" to best.latitude,
            "lon" to best.longitude,
            "accuracy" to best.accuracy.toDouble(),
            "time" to best.time
        )
    }

    private fun drawablePngBase64(drawable: Drawable): String {
        val size = 72
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
