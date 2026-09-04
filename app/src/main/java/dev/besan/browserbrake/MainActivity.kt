package dev.besan.browserbrake

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import dev.besan.browserbrake.rules.RuleRepository
import dev.besan.browserbrake.runtime.RuleRuntimeStore
import dev.besan.browserbrake.ui.BrowserBrakeApp
import dev.besan.browserbrake.ui.BrowserBrakeTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_HOME = "open_home"
    }

    private val homeRequestToken = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RuleRepository.ensureMigrated(this)
        RuleRuntimeStore.ensureMigrated(this)
        if (intent.getBooleanExtra(EXTRA_OPEN_HOME, false)) {
            homeRequestToken.intValue++
        }
        setContent {
            BrowserBrakeTheme {
                BrowserBrakeApp(homeRequestToken = homeRequestToken.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_HOME, false)) {
            homeRequestToken.intValue++
        }
    }
}
