package dev.besan.browserbrake

import android.os.Bundle
import android.widget.Toast
import dev.besan.browserbrake.runtime.RuleRuntimeStore

class UnlockGateActivity : BaseFlutterActivity() {
    companion object { const val EXTRA_RULE_ID = "rule_id" }
    override val flutterViewName: String = "unlock"

    override fun onCreate(savedInstanceState: Bundle?) {
        val id = intent.getStringExtra(EXTRA_RULE_ID).orEmpty()
        if (id.isBlank()) {
            Toast.makeText(this, "対象の制限を特定できませんでした", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (RuleRuntimeStore.state(this, id) != RuleRuntimeStore.STATE_READY) {
            Toast.makeText(this, "解除可能な状態ではありません", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        super.onCreate(savedInstanceState)
    }
}
