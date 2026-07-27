package com.tether.parent.ui.dashboard

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.tether.parent.R
import com.tether.parent.network.ApiClient
import com.tether.parent.network.SocketManager
import com.tether.parent.ui.monitoring.MultiChildFragment
import com.tether.parent.utils.Config
import com.tether.parent.utils.PreferenceManager
import com.tether.parent.utils.SecurityUtils
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var connectionStatus: TextView
    private lateinit var animeMascot: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ============================================================
        // SECURITY CHECK — run saat app start (anti-tampering)
        // ============================================================
        val securityReport = try {
            SecurityUtils.runSecurityChecks(this)
        } catch (e: Exception) {
            Log.e(TAG, "Security check failed: ${e.message}", e)
            null
        }
        if (securityReport != null) {
            Log.i(TAG, "Security: $securityReport")
            if (securityReport.threatLevel == SecurityUtils.ThreatLevel.HIGH) {
                Toast.makeText(this,
                    "⚠️ Security warning: ${securityReport.threatLevel}\nCheck logcat for details.",
                    Toast.LENGTH_LONG).show()
            }
        }

        try {
            setContentView(R.layout.activity_main)
        } catch (e: Exception) {
            Log.e(TAG, "setContentView failed: ${e.message}", e)
            Toast.makeText(this, "Layout error: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        try {
            connectionStatus = findViewById(R.id.connectionStatus)
            connectionStatus.text = "SYS:OFFLINE"
            connectionStatus.setTextColor(Color.parseColor("#404060"))

            animeMascot = findViewById(R.id.animeMascot)
            // CATATAN: VideoView TIDAK clickable, TIDAK focusable
            // Animasi auto-reactive ke status server via socket listener
            playAnime(AnimeState.IDLE)  // default: idle

            // Load dashboard fragment
            if (savedInstanceState == null) {
                try {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, MultiChildFragment())
                        .commit()
                } catch (e: Exception) {
                    Log.e(TAG, "Fragment load failed: ${e.message}", e)
                }
            }

            // Auto-connect ke server (background)
            autoConnect()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error: ${e.message}", e)
        }
    }

    /**
     * Play MP4 sesuai state koneksi.
     *  - IDLE  : idle loop (default, saat offline / standby)
     *  - HAPPY : saat socket connected (online)
     *  - SAD   : saat socket disconnected (offline/error)
     *
     * VideoView otomatis loop karena file MP4 durasi pendek diputar terus.
     */
    private fun playAnime(state: AnimeState) {
        try {
            val videoRes = when (state) {
                AnimeState.IDLE  -> R.raw.anime_idle
                AnimeState.HAPPY -> R.raw.anime_happy
                AnimeState.SAD   -> R.raw.anime_sad
            }
            val uri = Uri.parse("android.resource://$packageName/$videoRes")
            animeMascot.setVideoURI(uri)
            animeMascot.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)  // mute (no audio)
                animeMascot.start()
            }
            animeMascot.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "VideoView error: what=$what extra=$extra")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "VideoView play failed: ${e.message}", e)
        }
    }

    /**
     * Auto-connect ke server.
     * Tidak akan crash kalau server tidak ada — fallback ke SYS:OFFLINE.
     */
    private fun autoConnect() {
        Thread {
            try {
                val prefs = PreferenceManager.getInstance(this)

                if (prefs.isLoggedIn()) {
                    Log.d(TAG, "Already logged in, using saved token")
                    ApiClient.setToken(prefs.getToken())
                    runOnUiThread { connectWs(prefs) }
                    return@Thread
                }

                val deviceId = android.provider.Settings.Secure.getString(
                    contentResolver, android.provider.Settings.Secure.ANDROID_ID
                ) ?: UUID.randomUUID().toString().take(8)

                val email = "parent_$deviceId@tether.app"
                Log.d(TAG, "Attempting register: $email")
                val resp = ApiClient.register(email, "parent123", "Parent", "parent", null)

                if (resp != null && resp.has("token")) {
                    Log.d(TAG, "Register success, token received")
                    prefs.saveAuthResponse(resp)
                    ApiClient.setToken(prefs.getToken())
                    runOnUiThread { connectWs(prefs) }
                } else {
                    Log.w(TAG, "Register failed, staying offline")
                    runOnUiThread {
                        connectionStatus.text = "SYS:OFFLINE"
                        connectionStatus.setTextColor(Color.parseColor("#404060"))
                        // Animasi tetap idle (sudah di-set di onCreate)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto connect error: ${e.message}", e)
                runOnUiThread {
                    connectionStatus.text = "SYS:OFFLINE"
                    connectionStatus.setTextColor(Color.parseColor("#404060"))
                }
            }
        }.start()
    }

    /**
     * Connect ke WebSocket server.
     * Animasi OTOMATIS berubah sesuai status (IDLE → HAPPY → SAD → IDLE).
     */
    private fun connectWs(prefs: PreferenceManager) {
        try {
            val sk = SocketManager.getInstance()
            sk.connect(Config.SERVER_URL, prefs.getToken())

            // Socket connected → animasi HAPPY + status ONLINE
            sk.addEventListener("socket:connected") {
                runOnUiThread {
                    connectionStatus.text = "SYS:ONLINE"
                    connectionStatus.setTextColor(Color.parseColor("#00FFF5"))
                    playAnime(AnimeState.HAPPY)  // 🔄 auto-reactive
                }
                sk.authenticate(prefs.getUserId(), prefs.getUserRole(), prefs.getFamilyCode(), prefs.getUserName())
            }

            // Socket disconnected → animasi SAD + status OFFLINE
            sk.addEventListener("socket:disconnected") {
                runOnUiThread {
                    connectionStatus.text = "SYS:OFFLINE"
                    connectionStatus.setTextColor(Color.parseColor("#404060"))
                    playAnime(AnimeState.SAD)  // 🔄 auto-reactive
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connect WS error: ${e.message}", e)
        }
    }

    override fun onPause() {
        super.onPause()
        // Pause video saat activity di-background (hemat battery)
        try { if (::animeMascot.isInitialized) animeMascot.pause() } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        // Resume video saat activity kembali ke foreground
        try { if (::animeMascot.isInitialized) animeMascot.start() } catch (_: Exception) {}
    }

    private enum class AnimeState {
        IDLE,   // default loop (offline / standby)
        HAPPY,  // saat online
        SAD     // saat offline / error
    }

    companion object {
        private const val TAG = "TetherMain"
    }
}
