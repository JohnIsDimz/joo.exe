package com.tether.kids.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tether.kids.ui.access.SecretAccessActivity

/**
 * SecretCodeReceiver — Handle secret dial code *#*#TETHER#*#* (*#*#833843#*#*).
 *
 * Cara kerja:
 *  Android broadcast android.intent.action.SECRET_CODE saat user dial code
 *  dari phone app. Kita listen di receiver, lalu buka SecretAccessActivity.
 *
 *  Ini cara utama untuk buka Tether Kids saat launcher icon disembunyikan.
 */
class SecretCodeReceiver : BroadcastReceiver() {
    companion object { private const val TAG = "SecretCode" }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Secret code received: ${intent.dataString}")
        val i = Intent(context, SecretAccessActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(i)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open SecretAccess: ${e.message}")
        }
    }
}
