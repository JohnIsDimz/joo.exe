package com.xixfamily.kids.service

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.R
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.utils.PreferenceManager
import org.json.JSONObject

class NetworkMonitorService : Service() {

    companion object {
        private const val TAG = "NetworkMonitor"
        private const val CHANNEL_ID = "network_channel"
        private const val NOTIF_ID = 6001

        private var isRunning = false
        var lastSSID: String = "Not connected"
            private set
        var lastSignalStrength: Int = 0
            private set
        var lastIPAddress: String = ""
            private set

        fun isServiceRunning(): Boolean = isRunning
    }

    private lateinit var prefs: PreferenceManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private var lastReportTime = 0L
    private val reportInterval = 30000L // Report WiFi every 30s

    private val connectivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reportNetworkInfo()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getInstance(this)
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        isRunning = true
        createNotificationChannel()

        val notification = createNotification("Memonitor jaringan...")
        startForeground(NOTIF_ID, notification)

        registerReceiver(connectivityReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        Log.d(TAG, "NetworkMonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "REPORT" -> reportNetworkInfo()
            "STOP" -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        try { unregisterReceiver(connectivityReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun reportNetworkInfo() {
        if (!SocketManager.getInstance().isSocketConnected()) return

        try {
            val network = connectivityManager.activeNetwork ?: return
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return

            var ssid = "Not connected"
            var signalStrength = 0
            var ipAddress = ""

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo != null) {
                    ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: "Unknown"
                    signalStrength = wifiInfo.rssi
                    val ipInt = wifiInfo.ipAddress
                    ipAddress = String.format("%d.%d.%d.%d",
                        ipInt and 0xFF,
                        (ipInt shr 8) and 0xFF,
                        (ipInt shr 16) and 0xFF,
                        (ipInt shr 24) and 0xFF
                    )
                }
            }

            lastSSID = ssid
            lastSignalStrength = signalStrength
            lastIPAddress = ipAddress

            val now = System.currentTimeMillis()
            if (now - lastReportTime < reportInterval) return
            lastReportTime = now

            SocketManager.getInstance().emit("wifi:update", JSONObject().apply {
                put("ssid", ssid)
                put("bssid", wifiManager.connectionInfo?.bssid ?: "")
                put("signalStrength", signalStrength)
                put("ipAddress", ipAddress)
                put("isConnected", caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            })

            Log.d(TAG, "WiFi reported: $ssid ($signalStrength dBm)")
        } catch (e: Exception) {
            Log.e(TAG, "Error reporting WiFi: ${e.message}")
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Network Monitor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Network Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Monitoring jaringan WiFi" }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
