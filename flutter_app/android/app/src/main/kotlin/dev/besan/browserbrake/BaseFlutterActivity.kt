package dev.besan.browserbrake

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

abstract class BaseFlutterActivity : FlutterActivity() {
    protected abstract val flutterViewName: String

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        FlutterBridge.configure(this, flutterEngine, flutterViewName)
    }
}
