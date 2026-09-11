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
import java.io.ByteArrayOutputStream

object FlutterCatalogBridge {
    fun launchableApps(context: Context): List<Map<String, Any?>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                val pkg = activity.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
                val icon = runCatching { drawablePngBase64(info.loadIcon(pm)) }.getOrNull()
                mapOf("package" to pkg, "label" to label, "icon" to icon)
            }
            .distinctBy { it["package"] }
            .sortedBy { (it["label"] as? String)?.lowercase() }
            .toList()
    }

    fun places(context: Context): List<Map<String, Any?>> = PlaceStore.all(context).map {
        mapOf(
            "id" to it.id,
            "name" to it.name,
            "lat" to it.lat,
            "lon" to it.lon,
            "radiusM" to it.radiusM.toDouble(),
        )
    }

    fun addPlace(context: Context, name: String, lat: Double, lon: Double, radiusM: Float): Map<String, Any?> {
        val place = PlaceStore.add(context, name.ifBlank { "場所" }, lat, lon, radiusM)
        return mapOf("id" to place.id, "name" to place.name, "lat" to place.lat, "lon" to place.lon, "radiusM" to place.radiusM.toDouble())
    }

    fun currentLocation(context: Context): Map<String, Any?>? {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val candidates = manager.getProviders(true).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        val best: Location = candidates.maxByOrNull { it.time } ?: return null
        return mapOf("lat" to best.latitude, "lon" to best.longitude, "accuracy" to best.accuracy.toDouble(), "time" to best.time)
    }

    private fun drawablePngBase64(drawable: Drawable): String {
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
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
