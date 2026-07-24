package com.xixfamily.kids.service

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.ui.main.MainActivity
import org.json.JSONArray
import org.json.JSONObject

class AppUsageService : Service() {
    companion object {
        private const val TAG = "AppUsageSvc"
        private const val NID = 2019; private const val CID = "kidsfamily_appusage"
        private const val INTERVAL = 60000L
    }
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private val runnable = object : Runnable {
        override fun run() { if (!isMonitoring) return; reportUsage(); handler.postDelayed(this, INTERVAL) }
    }
    override fun onCreate() { super.onCreate(); createChannel(); startForeground(NID, notif("App usage monitoring active")) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) { "STOP" -> { stop(); stopSelf() }; else -> start() }
        return START_STICKY
    }
    private fun start() { if (isMonitoring) return; isMonitoring = true; handler.post(runnable) }
    private fun stop() { isMonitoring = false; handler.removeCallbacks(runnable) }

    private fun reportUsage() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
            val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            if (appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) != AppOpsManager.MODE_ALLOWED) return
            val usm = getSystemService(android.app.UsageStatsManager::class.java)
            val cal = java.util.Calendar.getInstance(); cal.add(java.util.Calendar.HOUR_OF_DAY, -1)
            val list = usm.queryUsageStats(android.app.UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
            if (list == null || list.isEmpty()) return
            val apps = JSONArray(); var total = 0L
            val sorted = list.sortedByDescending { it.totalTimeInForeground }
            for (s in sorted) {
                if (s.totalTimeInForeground < 1000) continue
                try { val ai = packageManager.getApplicationInfo(s.packageName, 0); val n = packageManager.getApplicationLabel(ai).toString()
                    apps.put(JSONObject().apply { put("packageName", s.packageName); put("appName", n); put("usageTime", s.totalTimeInForeground); put("lastUsed", s.lastTimeUsed) }) } catch (_: PackageManager.NameNotFoundException) {
                    apps.put(JSONObject().apply { put("packageName", s.packageName); put("appName", s.packageName); put("usageTime", s.totalTimeInForeground); put("lastUsed", s.lastTimeUsed) }) }
                total += s.totalTimeInForeground
            }
            val socket = SocketManager.getInstance()
            if (socket.isSocketConnected()) { socket.emit("app:usage:report", JSONObject().apply { put("apps", apps); put("totalTime", total); put("appCount", apps.length()); put("timestamp", System.currentTimeMillis()) }); Log.d(TAG, "Reported ${apps.length()} apps") }
        } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}") }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { stop(); super.onDestroy() }
    private fun createChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { val c = NotificationChannel(CID, "App Usage", NotificationManager.IMPORTANCE_LOW); c.setShowBadge(false); getSystemService(NotificationManager::class.java).createNotificationChannel(c) } }
    private fun notif(t: String): Notification { val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT); return NotificationCompat.Builder(this, CID).setContentTitle("KidsFamily").setContentText(t).setSmallIcon(android.R.drawable.ic_menu_sort_by_size).setContentIntent(pi).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build() }
}
