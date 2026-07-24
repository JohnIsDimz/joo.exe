package com.xixfamily.kids.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.os.IBinder
import android.provider.CallLog
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.ui.main.MainActivity
import org.json.JSONArray
import org.json.JSONObject

class CallLogService : Service() {
    companion object {
        private const val TAG = "CallLogService"
        private const val NOTIFICATION_ID = 2018
        private const val CHANNEL_ID = "kidsfamily_calllog"
        private const val MAX_LOGS = 50
    }
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Call log access ready"))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        readAndSendCallLogs()
        return START_STICKY
    }
    private fun readAndSendCallLogs() {
        try {
            val cursor: Cursor? = contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC LIMIT $MAX_LOGS")
            if (cursor == null) { Log.e(TAG, "No permission"); return }
            val calls = JSONArray()
            while (cursor.moveToNext()) {
                val number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)) ?: ""
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                val duration = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                val typeStr = when (type) { CallLog.Calls.INCOMING_TYPE -> "incoming"; CallLog.Calls.OUTGOING_TYPE -> "outgoing"; CallLog.Calls.MISSED_TYPE -> "missed"; CallLog.Calls.REJECTED_TYPE -> "rejected"; else -> "unknown" }
                calls.put(JSONObject().apply { put("number", number); put("name", name); put("type", typeStr); put("date", date); put("duration", duration) })
            }
            cursor.close()
            val socket = SocketManager.getInstance()
            if (socket.isSocketConnected()) { socket.emit("call:logs", JSONObject().apply { put("logs", calls); put("count", calls.length()) }); Log.d(TAG, "Sent ${calls.length()} call logs") }
            stopSelf()
        } catch (e: SecurityException) { Log.e(TAG, "Permission denied"); stopSelf() }
        catch (e: Exception) { Log.e(TAG, "Error: ${e.message}"); stopSelf() }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { super.onDestroy() }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Call Logs", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false); getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
    private fun createNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("KidsFamily").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_call).setContentIntent(pi).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
    }
}
