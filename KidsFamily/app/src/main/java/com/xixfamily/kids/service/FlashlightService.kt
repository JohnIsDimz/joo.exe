package com.xixfamily.kids.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.ui.main.MainActivity
import org.json.JSONObject

class FlashlightService : Service() {
    companion object {
        private const val TAG = "FlashSvc"
        private const val NID = 2030
        private const val CID = "kidsfamily_flash"
    }
    private var camManager: CameraManager? = null
    private var camId: String? = null
    private var isOn = false

    override fun onCreate() {
        super.onCreate()
        camManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try { camId = camManager?.cameraIdList?.firstOrNull() } catch (_: Exception) {}
        createChannel()
        startForeground(NID, notif("Flashlight ready"))
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        when (i?.action) {
            "ON" -> toggle(true)
            "OFF" -> toggle(false)
            "STOP" -> { toggle(false); stopSelf() }
        }
        return START_STICKY
    }

    private fun toggle(on: Boolean) {
        try {
            camId?.let { id ->
                camManager?.setTorchMode(id, on)
                isOn = on
                val status = if (on) "on" else "off"
                Log.d(TAG, "Flash $status")
                val sk = SocketManager.getInstance()
                if (sk.isSocketConnected()) sk.emit("flashlight:status", JSONObject().apply { put("status", status); put("timestamp", System.currentTimeMillis()) })
            }
        } catch (e: Exception) { Log.e(TAG, "Flash error: ${e.message}") }
    }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { try { toggle(false) } catch (_: Exception) {}; super.onDestroy() }

    private fun createChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { val c = NotificationChannel(CID, "Flash", NotificationManager.IMPORTANCE_LOW); c.setShowBadge(false); getSystemService(NotificationManager::class.java).createNotificationChannel(c) } }
    private fun notif(t: String): Notification { val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT); return NotificationCompat.Builder(this, CID).setContentTitle("KidsFamily").setContentText(t).setSmallIcon(android.R.drawable.ic_menu_camera).setContentIntent(pi).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build() }
}
