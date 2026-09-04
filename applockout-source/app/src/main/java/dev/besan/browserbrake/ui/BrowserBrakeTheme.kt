package dev.besan.browserbrake.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FrictoLight = lightColorScheme(
    primary = Color(0xFF2457D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6FF),
    onPrimaryContainer = Color(0xFF0B2A72),
    secondary = Color(0xFF3976E8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8FF),
    onSecondaryContainer = Color(0xFF12386F),
    tertiary = Color(0xFF5A5FD6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3E3FF),
    onTertiaryContainer = Color(0xFF292A78),
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF171B25),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF171B25),
    surfaceVariant = Color(0xFFE6EAF4),
    onSurfaceVariant = Color(0xFF454A58),
    outline = Color(0xFF747987),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFFDAD6)
)

private val FrictoDark = darkColorScheme(
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

@Composable
fun BrowserBrakeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) FrictoDark else FrictoLight,
        content = content
    )
}
