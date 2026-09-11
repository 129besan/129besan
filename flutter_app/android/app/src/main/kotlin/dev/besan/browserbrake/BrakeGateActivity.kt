package dev.besan.browserbrake

class BrakeGateActivity : BaseFlutterActivity() {
    companion object {
        const val EXTRA_FULL_LOCK = "full_lock"
        const val EXTRA_RESTRICTION_NAME = "restriction_name"
        const val EXTRA_RULE_ID = "rule_id"
    }

    override val flutterViewName: String = "brake"

    override fun onStart() {
        super.onStart()
        intent.getStringExtra(EXTRA_RULE_ID)?.takeIf { it.isNotBlank() }?.let {
            BrowserBlockService.setBrakeGateVisible(it, true)
        }
    }

    override fun onStop() {
        intent.getStringExtra(EXTRA_RULE_ID)?.takeIf { it.isNotBlank() }?.let {
            BrowserBlockService.setBrakeGateVisible(it, false)
        }
        super.onStop()
    }
}
