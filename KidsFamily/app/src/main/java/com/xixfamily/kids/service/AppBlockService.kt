package com.xixfamily.kids.service

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.R

class AppBlockService : Service() {

    companion object {
        private const val TAG = "AppBlock"
        private const val CHANNEL_ID = "app_block_channel"
        private const val NOTIF_ID = 8001

        private val blockedPackages = mutableSetOf<String>()
        private var isRunning = false

        fun isBlocked(packageName: String): Boolean = blockedPackages.contains(packageName)

        fun syncBlockedList(packages: List<String>) {
            blockedPackages.clear()
            blockedPackages.addAll(packages)
            Log.d(TAG, "Blocklist updated: ${blockedPackages.size} apps blocked")
        }

        fun addToBlocklist(packageName: String) {
            blockedPackages.add(packageName)
            Log.d(TAG, "Added to blocklist: $packageName")
        }

        fun removeFromBlocklist(packageName: String) {
            blockedPackages.remove(packageName)
            Log.d(TAG, "Removed from blocklist: $packageName")
        }

        fun isServiceRunning(): Boolean = isRunning
        fun getBlockedCount(): Int = blockedPackages.size
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()

        val notification = createNotification()
        startForeground(NOTIF_ID, notification)

        Log.d(TAG, "AppBlockService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SYNC" -> {
                val packages = intent.getStringArrayListExtra("packages")
                if (packages != null) {
                    syncBlockedList(packages)
                }
            }
            "ADD" -> {
                val pkg = intent.getStringExtra("packageName")
                if (pkg != null) addToBlocklist(pkg)
            }
            "REMOVE" -> {
                val pkg = intent.getStringExtra("packageName")
                if (pkg != null) removeFromBlocklist(pkg)
            }
            "STOP" -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Blocker")
            .setContentText("Monitoring ${blockedPackages.size} blocked apps")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Blocker",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Monitoring blocked apps" }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
