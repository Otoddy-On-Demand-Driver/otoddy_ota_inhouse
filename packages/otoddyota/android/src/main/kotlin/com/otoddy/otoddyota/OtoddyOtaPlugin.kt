package com.otoddy.otoddyota

import io.flutter.embedding.engine.plugins.FlutterPlugin

/** No-op plugin class required by Flutter's plugin registration system. */
class OtoddyOtaPlugin : FlutterPlugin {
    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        // The real work is done by OtoddyFlutterActivity.getFlutterShellArgs()
        // which runs before the engine initializes. This class is just needed
        // for Flutter's plugin registration.
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {}
}
