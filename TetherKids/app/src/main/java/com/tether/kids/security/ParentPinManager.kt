package com.tether.kids.security

import android.content.Context
import android.util.Log
import com.tether.kids.network.SocketManager
import com.tether.kids.utils.PreferenceManager
import org.json.JSONObject
import java.security.SecureRandom

/**
 * ParentPinManager — Generates and verifies 6-digit PIN for sensitive actions.
 *
 * Flow:
 *  1. Parent generate PIN dari Tether Parent app → `pin:generate` event
 *  2. Server save PIN hash + kirim `pin:generated` ke Tether Kids
 *  3. Anak coba uninstall/install restricted app → muncul dialog "Masukkan PIN ortu"
 *  4. Anak input PIN → kirim `pin:verify` ke server
 *  5. Server verify → reply `pin:verified` (true/false) ke Tether Kids
 *  6. Kalau true → action diizinkan, kalau false → blocked + notifikasi ke parent
 *
 * PIN disimpan di Tether Parent app (bukan di device anak),
 * sehingga anak tidak bisa bypass dengan uninstall Tether Kids.
 */
object ParentPinManager {
    private const val TAG = "ParentPin"
    private const val PIN_LENGTH = 6

    fun generatePin(context: Context): String {
        val random = SecureRandom()
        val pin = (0 until PIN_LENGTH).joinToString("") { random.nextInt(10).toString() }
        Log.d(TAG, "Generated new PIN (length $PIN_LENGTH)")
        return pin
    }

    fun sendPinToKid(context: Context, pin: String, ttlSeconds: Int = 600) {
        val prefs = PreferenceManager.getInstance(context)
        val sk = SocketManager.getInstance()
        if (!sk.isSocketConnected()) {
            Log.w(TAG, "Cannot send PIN: socket not connected")
            return
        }
        sk.emit("pin:generated", JSONObject().apply {
            put("targetUserId", prefs.getUserId())
            put("pin", pin)
            put("ttlSeconds", ttlSeconds)
            put("purpose", "uninstall_block")
            put("timestamp", System.currentTimeMillis())
        })
        Log.i(TAG, "PIN sent to kid (${prefs.getUserId()})")
    }

    /**
     * Build hash dari PIN untuk disimpan di server.
     * Server pakai bcrypt, di sini kita kirim plain (HTTPS nanti)
     * dan server yang hash.
     */
    fun hashPin(pin: String): String {
        // Simple hash — server akan re-hash dengan bcrypt
        return Integer.toHexString(pin.hashCode())
    }
}
