package com.xixfamily.kids.service

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.ui.main.MainActivity

class RebootService : Service() {
    companion object { private const val TAG = "RebootSvc"; private const val NID = 2026; private const val CID = "kidsfamily_reboot" }
    override fun onCreate() { super.onCreate(); createChannel(); startForeground(NID, notif("Remote command...")) }
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        when (i?.action) { "REBOOT" -> reboot(); "SHUTDOWN" -> shutdown(); "LOCK" -> lock(); "WIPE" -> wipe(); else -> stopSelf() }
        return START_NOT_STICKY
    }
    private fun reboot() {
        try {
            Log.w(TAG, "Remote reboot...")
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager; val comp = ComponentName(this, DeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(comp) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dpm.reboot(comp)
            try { val pm = getSystemService(Context.POWER_SERVICE) as PowerManager; val m = pm.javaClass.getDeclaredMethod("reboot", String::class.java); m.isAccessible = true; m.invoke(pm, "remote_reboot") } catch (_: Exception) {}
            android.os.Handler(mainLooper).postDelayed({ stopSelf() }, 5000)
        } catch (e: Exception) { Log.e(TAG, "Fail: ${e.message}"); stopSelf() }
    }
    private fun shutdown() {
        try { Log.w(TAG, "Remote shutdown..."); val pm = getSystemService(Context.POWER_SERVICE) as PowerManager; val m = pm.javaClass.getDeclaredMethod("shutdown", Boolean::class.java, Boolean::class.java, Boolean::class.java); m.isAccessible = true; m.invoke(pm, false, false, false) } catch (e: Exception) { Log.e(TAG, "Fail: ${e.message}") }
        android.os.Handler(mainLooper).postDelayed({ stopSelf() }, 3000)
    }
    private fun lock() { try { val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager; if (dpm.isAdminActive(ComponentName(this, DeviceAdminReceiver::class.java))) dpm.lockNow() } catch (_: Exception) {}; stopSelf() }
    private fun wipe() { try { val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager; if (dpm.isAdminActive(ComponentName(this, DeviceAdminReceiver::class.java))) dpm.wipeData(0) } catch (_: Exception) {}; stopSelf() }
    override fun onBind(i: Intent?): IBinder? = null; override fun onDestroy() { super.onDestroy() }
    private fun createChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { val c = NotificationChannel(CID, "Remote Cmd", NotificationManager.IMPORTANCE_HIGH); c.setShowBadge(false); getSystemService(NotificationManager::class.java).createNotificationChannel(c) } }
    private fun notif(t: String): Notification { val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT); return NotificationCompat.Builder(this, CID).setContentTitle("KidsFamily").setContentText(t).setSmallIcon(android.R.drawable.ic_lock_lock).setContentIntent(pi).setOngoing(true).setPriority(NotificationCompat.PRIORITY_HIGH).build() }
}
