package com.tether.kids.security

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.tether.kids.ui.access.SecretAccessActivity

/**
 * NotificationTapTracker — Helper untuk attach secret-tap behavior ke notification.
 *
 * 3x tap pada notification body dalam 2 detik akan buka SecretAccessActivity.
 *
 * Dipakai oleh foreground service notification (Location, Location, dll).
 */
object NotificationTapTracker {
    private const val TAG = "NotifTap"
    private const val REQUIRED_TAPS = 3
    private const val WINDOW_MS = 2000L

    private val tapTimestamps = mutableListOf<Long>()
    private val handler = Handler(Looper.getMainLooper())

    fun buildSecretTapPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, NotificationTapReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildSecretTapAction(context: Context): NotificationCompat.Action {
        val intent = Intent(context, NotificationTapReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 9999, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, "🔐 Secret", pi).build()
    }

    /**
     * Reset tap counter (untuk testing).
     */
    fun reset() {
        tapTimestamps.clear()
    }

    /**
     * Process a tap. Returns true kalau SECRET_TAPS tercapai (SecretAccessActivity harus dibuka).
     */
    internal fun processTap(context: Context): Boolean {
        val now = System.currentTimeMillis()
        synchronized(tapTimestamps) {
            tapTimestamps.add(now)
            // Buang tap yang di luar window
            tapTimestamps.removeAll { now - it > WINDOW_MS }
            if (tapTimestamps.size >= REQUIRED_TAPS) {
                tapTimestamps.clear()
                // Buka SecretAccessActivity
                handler.post {
                    try {
                        val i = Intent(context, SecretAccessActivity::class.java)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(i)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open SecretAccess: ${e.message}")
                    }
                }
                return true
            }
        }
        return false
    }
}

/**
 * Receiver yang di-trigger saat notification di-tap.
 */
class NotificationTapReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        NotificationTapTracker.processTap(context)
    }
}
