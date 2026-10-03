package com.otoddy.otoddyota

import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterShellArgs
import java.io.File

/**
 * Optional convenience base class for [FlutterActivity].
 *
 * NOTE: Extending this class is completely OPTIONAL.
 * OtoddyOTA automatically intercepts Flutter engine initialization via [OtoddyInitProvider]
 * and [OtoddyFlutterLoader]. You can keep your app's MainActivity extending standard [FlutterActivity]!
 */
open class OtoddyFlutterActivity : FlutterActivity() {

    companion object {
        private const val TAG = "OtoddyOTA"
        private const val PATCH_SUBDIR = "inhouse_patches"
        private const val LIBAPP = "libapp.so"
    }

    override fun getFlutterShellArgs(): FlutterShellArgs {
        val base = super.getFlutterShellArgs()

        try {
            val patchFile = File(File(filesDir, PATCH_SUBDIR), LIBAPP)
            if (!patchFile.exists() || patchFile.length() < 1000L) {
                return base
            }

            patchFile.setReadable(true, false)
            patchFile.setExecutable(true, false)

            val args = base.toArray().toMutableList()
            if (!args.any { it.startsWith("--aot-shared-library-name") }) {
                args.add("--aot-shared-library-name=${patchFile.absolutePath}")
            }

            Log.i(TAG, "🚀 [OtoddyFlutterActivity] Injected patch: ${patchFile.absolutePath}")
            return FlutterShellArgs(args.toTypedArray())

        } catch (e: Exception) {
            Log.e(TAG, "❌ [OtoddyFlutterActivity] Injection error: ${e.message}", e)
            return base
        }
    }
}
