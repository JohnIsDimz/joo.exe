package com.xixfamily.parent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.parent.ui.dashboard.MainActivity
import com.xixfamily.parent.network.SocketManager
import com.xixfamily.parent.utils.Config
import com.xixfamily.parent.utils.PreferenceManager

class WebSocketService : Service() {

    companion object {
        private const val TAG = "WebSocketService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "xixfamily_websocket"
        private const val CHANNEL_NAME = "XiXFamily Connection"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Memulai XiXFamily..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service starting")

        val prefs = PreferenceManager.getInstance(this)
        val token = prefs.getToken()
        val userId = prefs.getUserId()
        val userName = prefs.getUserName()
        val userRole = prefs.getUserRole()
        val familyCode = prefs.getFamilyCode()

        if (token.isNotEmpty() && userId.isNotEmpty()) {
            connectToServer(token, userId, userName, userRole, familyCode)
        }

        return START_STICKY
    }

    private fun connectToServer(token: String, userId: String, userName: String, userRole: String, familyCode: String) {
        // Gunakan URL hardcoded dari Config
        val socketManager = SocketManager.getInstance()
        socketManager.connect(Config.SERVER_URL, token)

        socketManager.addEventListener("socket:connected") {
            updateNotification("Terhubung - $userName")
            socketManager.authenticate(userId, userRole, familyCode, userName)
        }

        socketManager.addEventListener("socket:disconnected") {
            updateNotification("Menghubungkan ulang...")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        SocketManager.getInstance().disconnect()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Koneksi background XiXFamily"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("XiXFamily")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }
}
