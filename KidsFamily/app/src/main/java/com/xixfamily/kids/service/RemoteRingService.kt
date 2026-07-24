package com.xixfamily.kids.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.ui.main.MainActivity

class RemoteRingService : Service() {
    companion object { private const val TAG = "RemoteRing"; private const val NID = 2022; private const val CID = "kidsfamily_ring"; private const val DURATION = 30000L }
    private var mp: MediaPlayer? = null; private var vibrator: Vibrator? = null
    override fun onCreate() { super.onCreate(); vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator; createChannel(); startForeground(NID, notif("Ringing...")) }
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int { when (i?.action) { "STOP" -> stopRing(); else -> startRing() }; return START_STICKY }
    private fun startRing() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager; am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mp = MediaPlayer().apply { setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()); setDataSource(this@RemoteRingService, uri); isLooping = true; setVolume(1.0f, 1.0f); prepare(); start(); Log.d(TAG, "Ringing") }
            if (vibrator?.hasVibrator() == true) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0,500,300,500), intArrayOf(0,255,0,255), -1)) else vibrator?.vibrate(longArrayOf(0,500,300,500), 0) }
            Handler(mainLooper).postDelayed({ stopRing(); stopSelf() }, DURATION)
        } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}"); stopSelf() }
    }
    private fun stopRing() { try { mp?.apply { if (isPlaying) stop(); release() }; mp = null; vibrator?.cancel() } catch (_: Exception) {}; stopForeground(STOP_FOREGROUND_REMOVE) }
    override fun onBind(i: Intent?): IBinder? = null; override fun onDestroy() { stopRing(); super.onDestroy() }
    private fun createChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { val c = NotificationChannel(CID, "Remote Ring", NotificationManager.IMPORTANCE_HIGH); c.setShowBadge(false); c.setSound(null, null); getSystemService(NotificationManager::class.java).createNotificationChannel(c) } }
    private fun notif(t: String): Notification { val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT); return NotificationCompat.Builder(this, CID).setContentTitle("KidsFamily").setContentText(t).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentIntent(pi).setOngoing(true).setPriority(NotificationCompat.PRIORITY_HIGH).build() }
}
