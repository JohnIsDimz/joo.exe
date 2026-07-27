package com.tether.kids.security

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.tether.kids.network.SocketManager
import com.tether.kids.utils.PreferenceManager
import org.json.JSONObject

/**
 * InstallInterceptor — Listen untuk package install attempts dan kirim ke server.
 *
 * Flow:
 *  - User tap "Install" di app store / APK installer
 *  - PackageInstaller session created
 *  - Kami dengar via BroadcastReceiver (ACTION_PACKAGE_ADDED)
 *  - Atau via PackageInstallObserver (deprecated but still works)
 *
 * Untuk真正的拦截 (block install) butuh Device Owner dengan setUninstallBlocked
 * dan install restriction API. Tanpa DO, kita cuma detect + notify parent.
 */
class InstallInterceptor : BroadcastReceiver() {

    companion object {
        private const val TAG = "InstallInter"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val pkg = intent.data?.schemeSpecificPart ?: return
                val replaced = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!replaced && pkg != context.packageName) {
                    Log.i(TAG, "App installed: $pkg")
                    notifyParent(context, "installed", pkg)
                }
            }
        }
    }

    private fun notifyParent(context: Context, action: String, packageName: String) {
        val prefs = PreferenceManager.getInstance(context)
        val sk = SocketManager.getInstance()
        if (sk.isSocketConnected()) {
            sk.emit("app:install:attempt", JSONObject().apply {
                put("action", action)
                put("packageName", packageName)
                put("userId", prefs.getUserId())
                put("familyCode", prefs.getFamilyCode())
                put("severity", "WARN")
                put("message", "App $action: $packageName")
                put("requiresPin", true)
                put("timestamp", System.currentTimeMillis())
            })
        }

        // Minta user verifikasi via PIN
        PinVerificationActivity.launchForUninstall(context, packageName)
    }
}
