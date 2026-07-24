package com.xixfamily.kids.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.ui.main.MainActivity
import org.json.JSONObject

class ClipboardMonitorService : Service() {
    companion object {
        private const val TAG = "ClipboardService"
        private const val NOTIFICATION_ID = 2017
        private const val CHANNEL_ID = "kidsfamily_clipboard"
        private const val POLL_INTERVAL_MS = 3000L
    }
    private lateinit var clipboardManager: ClipboardManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastClipboard = ""
    private var isMonitoring = false
    private val clipboardRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return
            checkClipboard()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }
    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Clipboard monitoring active"))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> { stopMonitoring(); stopSelf() }
            else -> startMonitoring()
        }
        return START_STICKY
    }
    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        handler.post(clipboardRunnable)
        Log.d(TAG, "Clipboard monitoring started")
    }
    private fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacks(clipboardRunnable)
    }
    private fun checkClipboard() {
        try {
            if (!clipboardManager.hasPrimaryClip()) return
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0)?.text?.toString() ?: return
            if (text.isEmpty() || text == lastClipboard) return
            lastClipboard = text
            val label = clip.description?.label?.toString() ?: "unknown"
            val data = JSONObject().apply {
                put("text", text.substring(0, minOf(text.length, 500)))
                put("label", label)
                put("length", text.length)
            }
            val socket = SocketManager.getInstance()
            if (socket.isSocketConnected()) {
                socket.emit("clipboard:update", data)
                Log.d(TAG, "Clipboard: ${text.take(50)}...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard error: ${e.message}")
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { stopMonitoring(); super.onDestroy() }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Clipboard", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
    private fun createNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("KidsFamily").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_edit).setContentIntent(pi).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
    }
}
