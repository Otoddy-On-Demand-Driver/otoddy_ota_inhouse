package com.otoddy.otoddyota

import android.content.Context
import android.os.Build
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
        private const val PREFS_NAME = "otoddy_ota_prefs"
        private const val KEY_LAST_VERSION_CODE = "last_installed_version_code"
        private const val KEY_LAST_UPDATE_TIME = "last_installed_update_time"
    }

    override fun getFlutterShellArgs(): FlutterShellArgs {
        val base = super.getFlutterShellArgs()

        try {
            val patchDir = File(filesDir, PATCH_SUBDIR)
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val pInfo = try {
                packageManager.getPackageInfo(packageName, 0)
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
            // If base app was updated (new AAB/APK installed) or first run of this version check,
            // wipe any stale legacy patches from previous builds so new bundle changes show immediately!
            val isBaseAppUpdated = (lastVersionCode == -1L) ||
                    (currentVersionCode != -1L && currentVersionCode != lastVersionCode) ||
                    (lastUpdateTime != 0L && currentUpdateTime > lastUpdateTime)

            if (isBaseAppUpdated) {
                Log.i(TAG, "🆕 [OtoddyFlutterActivity] Base app update / initial run detected! Purging old patches...")
                if (patchDir.exists()) {
                    patchDir.deleteRecursively()
                }
                prefs.edit()
                    .putLong(KEY_LAST_VERSION_CODE, currentVersionCode)
                    .putLong(KEY_LAST_UPDATE_TIME, currentUpdateTime)
                    .apply()
                return base
            }

            val patchFile = File(patchDir, LIBAPP)
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
