package com.xixfamily.kids.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.ui.main.MainActivity

class RemoteNotificationService : Service() {
    companion object {
        private const val TAG = "RemoteNotif"
        private const val CID_NORMAL = "kidsfamily_remote_notif"
        private const val CID_ALERT = "kidsfamily_remote_alert"
        private var counter = 10000
    }
    override fun onCreate() { super.onCreate(); createChannels() }
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        when (i?.action) {
            "STOP" -> stopSelf()
            "SHOW_NOTIFICATION" -> showNotif(i.getStringExtra("title") ?: "Notif", i.getStringExtra("body") ?: "", i.getStringExtra("priority") ?: "normal", i.getStringExtra("targetApp") ?: "")
        }
        return START_NOT_STICKY
    }
    private fun showNotif(title: String, body: String, priority: String, targetApp: String) {
        try {
            val nid = counter++
            val pi = PendingIntent.getActivity(this, nid, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val cid = if (priority == "critical") CID_ALERT else CID_NORMAL
            val imp = if (priority == "high" || priority == "critical") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            val b = NotificationCompat.Builder(this, cid).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setContentIntent(pi).setAutoCancel(true).setPriority(imp).setCategory(NotificationCompat.CATEGORY_ALARM)
            if (priority == "critical") { b.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)); b.setVibrate(longArrayOf(0, 500, 200, 500)) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) b.setFullScreenIntent(pi, priority == "critical")
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(nid, b.build())
            Log.d(TAG, "Notif shown: $title")
        } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}") }
    }
    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(CID_NORMAL, "Remote Notif", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "From parents"; setShowBadge(true); enableLights(true); getSystemService(NotificationManager::class.java).createNotificationChannel(this) }
            val s = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            NotificationChannel(CID_ALERT, "Critical", NotificationManager.IMPORTANCE_HIGH).apply { description = "Critical alerts"; setShowBadge(true); enableVibration(true); setSound(s, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()); getSystemService(NotificationManager::class.java).createNotificationChannel(this) }
        }
    }
    override fun onBind(i: Intent?): IBinder? = null; override fun onDestroy() { super.onDestroy() }
}
