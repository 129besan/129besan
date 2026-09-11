package dev.besan.browserbrake

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationManager
import android.util.Base64
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
                    "addPlace" -> {
                        val name = call.argument<String>("name").orEmpty()
                        val lat = call.argument<Number>("lat")?.toDouble() ?: 0.0
                        val lon = call.argument<Number>("lon")?.toDouble() ?: 0.0
                        val radius = call.argument<Number>("radiusM")?.toFloat() ?: 250f
                        result.success(addPlace(activity, name, lat, lon, radius))
                    }
                    "deletePlace" -> {
                        call.argument<String>("id")?.let { PlaceStore.delete(activity, it) }
                        result.success(null)
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
        return pm.queryIntentActivities(intent, 0).asSequence().mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val pkg = activity.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            val icon = runCatching { drawablePngBase64(info.loadIcon(pm)) }.getOrNull()
            mapOf("package" to pkg, "label" to label, "icon" to icon)
        }.distinctBy { it["package"] }.sortedBy { (it["label"] as? String)?.lowercase() }.toList()
    }

    private fun places(context: Context): List<Map<String, Any?>> = PlaceStore.all(context).map {
        mapOf("id" to it.id, "name" to it.name, "lat" to it.lat, "lon" to it.lon, "radiusM" to it.radiusM.toDouble())
    }

    private fun addPlace(context: Context, name: String, lat: Double, lon: Double, radiusM: Float): Map<String, Any?> {
        val place = PlaceStore.add(context, name.ifBlank { "場所" }, lat, lon, radiusM)
        return mapOf("id" to place.id, "name" to place.name, "lat" to place.lat, "lon" to place.lon, "radiusM" to place.radiusM.toDouble())
    }

    private fun currentLocation(context: Context): Map<String, Any?>? {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val best: Location = manager.getProviders(true).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time } ?: return null
        return mapOf("lat" to best.latitude, "lon" to best.longitude, "accuracy" to best.accuracy.toDouble(), "time" to best.time)
    }

    private fun drawablePngBase64(drawable: Drawable): String {
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) drawable.bitmap else {
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
            Bitmap.createBitmap(width.coerceAtMost(192), height.coerceAtMost(192), Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
