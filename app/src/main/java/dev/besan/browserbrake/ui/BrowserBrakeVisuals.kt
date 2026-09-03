package dev.besan.browserbrake.ui

import androidx.compose.foundation.isSystemInDarkTheme
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
        return if (isSystemInDarkTheme()) {
            RuntimeTone(Color(0xFF4B1F22), Color(0xFFFFDAD6), Color(0xFFFFB4AB))
        } else {
            RuntimeTone(Color(0xFFFFE2E0), Color(0xFF8C1D18), Color(0xFFB3261E))
        }
    }

    val dark = isSystemInDarkTheme()
    return when (state) {
        Prefs.STATE_CHALLENGING -> if (dark) {
            RuntimeTone(Color(0xFF443A18), Color(0xFFFFE9A8), Color(0xFFFFD54F))
        } else {
            RuntimeTone(Color(0xFFFFF3C4), Color(0xFF5D4A00), Color(0xFFE0A800))
        }
        Prefs.STATE_READY -> if (dark) {
            RuntimeTone(Color(0xFF153D38), Color(0xFFC8F5EA), Color(0xFF62D8C3))
        } else {
            RuntimeTone(Color(0xFFDDF7F1), Color(0xFF005B4E), Color(0xFF008577))
        }
        Prefs.STATE_SESSION -> if (dark) {
            RuntimeTone(Color(0xFF183C28), Color(0xFFD1F3DD), Color(0xFF69D18B))
        } else {
            RuntimeTone(Color(0xFFE2F5E8), Color(0xFF145C35), Color(0xFF2E8B57))
        }
        Prefs.STATE_RECOVERY -> if (dark) {
            RuntimeTone(Color(0xFF352B49), Color(0xFFE8DEFF), Color(0xFFCAB4FF))
        } else {
            RuntimeTone(Color(0xFFF0E9FF), Color(0xFF5E3D91), Color(0xFF7E57C2))
        }
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
