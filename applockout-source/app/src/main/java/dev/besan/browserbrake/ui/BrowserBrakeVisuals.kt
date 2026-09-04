package dev.besan.browserbrake.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.besan.browserbrake.Prefs
import dev.besan.browserbrake.rules.BrowserRule

data class RuntimeTone(
    val container: Color,
    val content: Color,
    val accent: Color
)

@Composable
fun runtimeTone(state: String, overLimit: Boolean): RuntimeTone {
    if (overLimit) {
        return RuntimeTone(Color(0xFF4B1F22), Color(0xFFFFDAD6), Color(0xFFFFB4AB))
    }

    return when (state) {
        Prefs.STATE_CHALLENGING ->
            RuntimeTone(Color(0xFF162E5F), Color(0xFFDDE7FF), Color(0xFF8FB2FF))
        Prefs.STATE_READY ->
            RuntimeTone(Color(0xFF12395A), Color(0xFFD8EEFF), Color(0xFF75C3FF))
        Prefs.STATE_SESSION ->
            RuntimeTone(Color(0xFF102F50), Color(0xFFDCEBFF), Color(0xFF86B8FF))
        Prefs.STATE_RECOVERY ->
            RuntimeTone(Color(0xFF292B62), Color(0xFFE5E3FF), Color(0xFFB6B7FF))
        else -> RuntimeTone(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun RuleIconBadge(rule: BrowserRule, modifier: Modifier = Modifier) {
    val glyph = when {
        rule.browsers && rule.sns -> "◎"
        rule.browsers -> "🌐"
        rule.sns -> "●"
        else -> "▦"
    }
    Surface(
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(glyph, fontWeight = FontWeight.Bold)
        }
    }
}
