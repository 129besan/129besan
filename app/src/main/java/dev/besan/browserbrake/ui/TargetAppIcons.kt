package dev.besan.browserbrake.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import dev.besan.browserbrake.TargetApps
import dev.besan.browserbrake.rules.BrowserRule
import dev.besan.browserbrake.rules.TargetGroupCatalog

@Composable
fun TargetAppIcons(
    context: Context,
    rule: BrowserRule,
    modifier: Modifier = Modifier,
    maxIcons: Int = 4
) {
    val packages = remember(rule.id, rule.browsers, rule.sns, rule.customPackages) {
        targetPackagesForDisplay(context, rule)
    }
    if (packages.isEmpty()) return

    val shown = packages.take(maxIcons)
    val extra = (packages.size - shown.size).coerceAtLeast(0)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        shown.forEach { pkg ->
            val bitmap = remember(pkg) {
                runCatching {
                    drawableToImageBitmap(context.packageManager.getApplicationIcon(pkg))
                }.getOrNull()
            }
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        if (extra > 0) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("+$extra", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

fun targetPackagesForDisplay(context: Context, rule: BrowserRule): List<String> {
    val packages = linkedSetOf<String>()
    if (rule.browsers) packages += TargetApps.browserPackages(context)
    if (rule.sns) packages += TargetGroupCatalog.SNS_PACKAGES
    packages += rule.customPackages

    return packages.filter { pkg ->
        runCatching {
            context.packageManager.getApplicationIcon(pkg)
            true
        }.getOrDefault(false)
    }
}

private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap.asImageBitmap()
    }

    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}
