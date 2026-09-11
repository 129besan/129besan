package dev.besan.browserbrake

import android.os.Bundle
import dev.besan.browserbrake.rules.RuleRepository
import dev.besan.browserbrake.runtime.RuleRuntimeStore

class MainActivity : BaseFlutterActivity() {
    companion object {
        const val EXTRA_OPEN_HOME = "open_home"
    }

    override val flutterViewName: String = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        RuleRepository.ensureMigrated(this)
        RuleRuntimeStore.ensureMigrated(this)
        RuleRuntimeStore.reconcilePersistentState(this)
        super.onCreate(savedInstanceState)
    }
}
