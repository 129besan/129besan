package dev.besan.browserbrake.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The Pixel battery-saver/dark appearance became the visual direction for AppLockout.
// Keep this palette stable regardless of system theme so the product has one identity.
private val AppLockoutColors = darkColorScheme(
    primary = Color(0xFFB5C7FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF123F9E),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFAFC6FF),
    onSecondary = Color(0xFF002E6A),
    secondaryContainer = Color(0xFF17458F),
    onSecondaryContainer = Color(0xFFD9E5FF),
    tertiary = Color(0xFFC7C6FF),
    onTertiary = Color(0xFF303080),
    tertiaryContainer = Color(0xFF45469A),
    onTertiaryContainer = Color(0xFFE2E0FF),
    background = Color(0xFF10131B),
    onBackground = Color(0xFFE4E7F1),
    surface = Color(0xFF151821),
    onSurface = Color(0xFFE4E7F1),
    surfaceVariant = Color(0xFF414754),
    onSurfaceVariant = Color(0xFFC5C9D4),
    outline = Color(0xFF8F94A2),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A)
)

private val AppLockoutShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(38.dp)
)

@Composable
fun BrowserBrakeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppLockoutColors,
        shapes = AppLockoutShapes,
        content = content
    )
}
