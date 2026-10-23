package com.otoddy.otoddyota

import android.content.Context
import android.os.Build
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
 *
 * If a new APK/AAB update is detected (versionCode or lastUpdateTime changed),
 * it automatically clears stale patches from previous releases so new bundle changes show immediately.
 */
class OtoddyFlutterLoader : FlutterLoader() {

    companion object {
        private const val TAG = "OtoddyOTA"
        private const val PATCH_DIR = "inhouse_patches"
        private const val PATCH_SO = "libapp.so"
        private const val PREFS_NAME = "otoddy_ota_prefs"
        private const val KEY_LAST_VERSION_CODE = "last_installed_version_code"
        private const val KEY_LAST_UPDATE_TIME = "last_installed_update_time"
    }

    private fun injectPatchArgs(context: Context, args: Array<String>?): Array<String> {
        try {
            val patchDir = File(context.filesDir, PATCH_DIR)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Detect whether the base APK was updated
            val pInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) {
                null
            }

            val currentVersionCode = if (pInfo != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.versionCode.toLong()
                }
            } else {
                -1L
            }
            val currentUpdateTime = pInfo?.lastUpdateTime ?: 0L

            val lastVersionCode = prefs.getLong(KEY_LAST_VERSION_CODE, -1L)
            val lastUpdateTime = prefs.getLong(KEY_LAST_UPDATE_TIME, 0L)

            // If base app was updated (new AAB/APK installed) or first run of this version check,
            // wipe any stale legacy patches from previous builds so new bundle changes show immediately!
            val isBaseAppUpdated = (lastVersionCode == -1L) ||
                    (currentVersionCode != -1L && currentVersionCode != lastVersionCode) ||
                    (lastUpdateTime != 0L && currentUpdateTime > lastUpdateTime)

            if (isBaseAppUpdated) {
                Log.i(TAG, "🆕 [OtoddyOTA] Base app update / initial run detected (vCode: $lastVersionCode -> $currentVersionCode, updated: $lastUpdateTime -> $currentUpdateTime)! Wiping stale OTA patches...")
                if (patchDir.exists()) {
                    patchDir.deleteRecursively()
                }
                prefs.edit()
                    .putLong(KEY_LAST_VERSION_CODE, currentVersionCode)
                    .putLong(KEY_LAST_UPDATE_TIME, currentUpdateTime)
                    .apply()
                return args ?: emptyArray()
            }

            val patchFile = File(patchDir, PATCH_SO)
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
