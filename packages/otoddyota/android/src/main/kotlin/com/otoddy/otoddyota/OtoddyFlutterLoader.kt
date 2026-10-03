package com.otoddy.otoddyota

import android.content.Context
import android.os.Handler
import android.util.Log
import io.flutter.embedding.engine.loader.FlutterLoader
import java.io.File

/**
 * Custom FlutterLoader that automatically detects downloaded OTA patches
 * and passes `--aot-shared-library-name` to Flutter's native engine.
 *
 * Flutter's FlutterLoader requires the library path to be inside context.filesDir
 * and end with ".so" to pass getSafeAotSharedLibraryName validation.
 */
class OtoddyFlutterLoader : FlutterLoader() {

    companion object {
        private const val TAG = "OtoddyOTA"
        private const val PATCH_DIR = "inhouse_patches"
        private const val PATCH_SO = "libapp.so"
    }

    private fun injectPatchArgs(context: Context, args: Array<String>?): Array<String> {
        try {
            val patchFile = File(File(context.filesDir, PATCH_DIR), PATCH_SO)
            if (!patchFile.exists() || patchFile.length() < 1000L) {
                Log.d(TAG, "No OTA patch found at ${patchFile.absolutePath} -> loading baseline APK")
                return args ?: emptyArray()
            }

            // Ensure permissions
            patchFile.setReadable(true, false)
            patchFile.setExecutable(true, false)

            Log.i(TAG, "🚀 [OtoddyOTA] Loading active patch: ${patchFile.absolutePath} (${patchFile.length()} bytes)")

            val patchArg = "--aot-shared-library-name=${patchFile.absolutePath}"
            val argList = ArrayList<String>()
            argList.add(patchArg)
            args?.forEach { arg ->
                if (!arg.startsWith("--aot-shared-library-name")) {
                    argList.add(arg)
                }
            }
            return argList.toTypedArray()
        } catch (t: Throwable) {
            Log.e(TAG, "❌ [OtoddyOTA] Error reading patch: ${t.message}", t)
            return args ?: emptyArray()
        }
    }

    override fun ensureInitializationComplete(context: Context, args: Array<String>?) {
        val newArgs = injectPatchArgs(context, args)
        super.ensureInitializationComplete(context, newArgs)
    }

    override fun ensureInitializationCompleteAsync(
        context: Context,
        args: Array<String>?,
        callbackHandler: Handler,
        callback: Runnable
    ) {
        val newArgs = injectPatchArgs(context, args)
        super.ensureInitializationCompleteAsync(context, newArgs, callbackHandler, callback)
    }
}
