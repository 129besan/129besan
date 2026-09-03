package dev.besan.browserbrake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.besan.browserbrake.rules.RuleRepository
import dev.besan.browserbrake.ui.BrowserBrakeApp
import dev.besan.browserbrake.ui.BrowserBrakeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RuleRepository.ensureMigrated(this)
        setContent {
            BrowserBrakeTheme {
                BrowserBrakeApp()
            }
        }
    }
}
