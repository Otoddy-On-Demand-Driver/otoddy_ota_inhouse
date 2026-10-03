package com.otoddy.otoddyota

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import io.flutter.FlutterInjector

/**
 * Initializes OtoddyOTA custom FlutterLoader before Application or Activity startup.
 * Automatically merged into the host app's AndroidManifest.xml by Gradle.
 * Requires ZERO manual setup in the host app's MainActivity or AndroidManifest.
 */
class OtoddyInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        injectFlutterLoader()
        return true
    }

    private fun injectFlutterLoader() {
        try {
            Log.i("OtoddyOTA", "OtoddyInitProvider: Registering OtoddyFlutterLoader into FlutterInjector...")

            val customLoader = OtoddyFlutterLoader()
            val customInjector = FlutterInjector.Builder()
                .setFlutterLoader(customLoader)
                .build()

            // 1. Try standard FlutterInjector.setInstance
            try {
                FlutterInjector.reset()
                FlutterInjector.setInstance(customInjector)
                Log.i("OtoddyOTA", "✅ [OtoddyInitProvider] Registered via FlutterInjector.setInstance()")
                return
            } catch (t: Throwable) {
                Log.w("OtoddyOTA", "Standard setInstance threw (${t.message}), applying reflection injection...")
            }

            // 2. Direct Reflection fallback (guaranteed to succeed regardless of accessed flag or R8)
            val injectorClass = FlutterInjector::class.java
            val instanceField = injectorClass.getDeclaredField("instance").apply { isAccessible = true }
            instanceField.set(null, customInjector)

            try {
                val accessedField = injectorClass.getDeclaredField("accessed").apply { isAccessible = true }
                accessedField.setBoolean(null, true)
            } catch (_: Throwable) {}

            Log.i("OtoddyOTA", "✅ [OtoddyInitProvider] Registered via reflection injection!")

        } catch (t: Throwable) {
            Log.e("OtoddyOTA", "❌ OtoddyInitProvider failed to register OtoddyFlutterLoader: ${t.message}", t)
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
