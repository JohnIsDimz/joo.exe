package com.xixfamily.kids.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.ui.main.MainActivity
import org.json.JSONObject

class BatteryMonitorService : Service() {
    companion object { private const val TAG = "BattMon"; private const val NID = 2024; private const val CID = "kidsfamily_batt"; private const val THRESHOLD = 5 }
    private var lastLevel = -1; private var lastPlug = -1
    private val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { handle(i) } }
    override fun onCreate() {
        super.onCreate(); createChannel(); startForeground(NID, notif("Battery monitoring active"))
        val f = IntentFilter().apply { addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_BATTERY_LOW); addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED) }
        registerReceiver(receiver, f)
        val init = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)); if (init != null) handle(init)
    }
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int { if (i?.action == "STOP") stopSelf(); return START_STICKY }
    private fun handle(i: Intent) {
        try {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f; val volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            val plug = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0); val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1); val tech = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
            if (kotlin.math.abs(pct - lastLevel) < THRESHOLD && plug == lastPlug && lastLevel >= 0) return
            lastLevel = pct; lastPlug = plug
            val plugStr = when(plug) { BatteryManager.BATTERY_PLUGGED_AC->"ac"; BatteryManager.BATTERY_PLUGGED_USB->"usb"; BatteryManager.BATTERY_PLUGGED_WIRELESS->"wireless"; else->"not_charging" }
            val statusStr = when(status) { BatteryManager.BATTERY_STATUS_CHARGING->"charging"; BatteryManager.BATTERY_STATUS_FULL->"full"; BatteryManager.BATTERY_STATUS_DISCHARGING->"discharging"; else->"unknown" }
            val d = JSONObject().apply { put("level", pct); put("plugged", plugStr); put("status", statusStr); put("temperature", temp); put("voltage", volt); put("technology", tech); put("isCharging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL); put("timestamp", System.currentTimeMillis()) }
            val socket = SocketManager.getInstance(); if (socket.isSocketConnected()) { socket.emit("battery:update", d); Log.d(TAG, "Batt: $pct%") }
        } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}") }
    }
    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { try { unregisterReceiver(receiver) } catch (_: Exception) {}; super.onDestroy() }
    private fun createChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { val c = NotificationChannel(CID, "Battery", NotificationManager.IMPORTANCE_LOW); c.setShowBadge(false); getSystemService(NotificationManager::class.java).createNotificationChannel(c) } }
    private fun notif(t: String): Notification { val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT); return NotificationCompat.Builder(this, CID).setContentTitle("KidsFamily").setContentText(t).setSmallIcon(android.R.drawable.ic_lock_idle_charging).setContentIntent(pi).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build() }
}
