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
import com.xixfamily.kids.utils.PreferenceManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class VoiceMonitorService : Service() {

    companion object {
        private const val TAG = "VoiceMonitor"
        private const val CHANNEL_ID = "voice_monitor_channel"
        private const val NOTIF_ID = 9003

        // Audio recording parameters
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_DURATION_MS = 2000L // 2 second chunks
        private const val BUFFER_SIZE = 4096

        var isRecording = false
            private set
        var currentSessionId: String? = null
            private set

        // Callback for UI updates
        var onStatusChange: ((Boolean, String?) -> Unit)? = null
    }

    private lateinit var prefs: PreferenceManager
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var sequenceNumber = 0

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getInstance(this)
        createNotificationChannel()
        Log.d(TAG, "VoiceMonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_VOICE" -> {
                val sessionId = intent.getStringExtra("sessionId") ?: return START_STICKY
                startRecording(sessionId)
            }
            "STOP_VOICE" -> {
                stopRecording()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    private fun startRecording(sessionId: String) {
        if (isRecording) return

        currentSessionId = sessionId
        sequenceNumber = 0
        isRecording = true

        val notification = createNotification("Mendengarkan suara sekitar...")
        startForeground(NOTIF_ID, notification)

        onStatusChange?.invoke(true, sessionId)

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
                    Log.e(TAG, "AudioRecord initialization failed")
                    SocketManager.getInstance().emit("voice:error", JSONObject().apply {
                        put("sessionId", sessionId)
                        put("message", "Gagal inisialisasi microphone")
                    })
                    isRecording = false
                    return@Thread
                }

                audioRecord?.startRecording()
                Log.d(TAG, "Voice recording started for session $sessionId")

                val buffer = ByteArray(BUFFER_SIZE)
                val chunkBuffer = ByteArrayOutputStream()

                while (isRecording && !Thread.currentThread().isInterrupted) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                    if (bytesRead > 0) {
                        chunkBuffer.write(buffer, 0, bytesRead)

                        // Send chunk every CHUNK_DURATION_MS worth of data
                        val chunkSize = (SAMPLE_RATE * 2 * CHUNK_DURATION_MS / 1000).toInt()
                        if (chunkBuffer.size() >= chunkSize) {
                            sendAudioChunk(sessionId, chunkBuffer.toByteArray())
                            chunkBuffer.reset()
                        }
                    }
                }

                // Send any remaining data
                if (chunkBuffer.size() > 0) {
                    sendAudioChunk(sessionId, chunkBuffer.toByteArray())
                }

            } catch (e: Exception) {
                Log.e(TAG, "Recording error: ${e.message}")
                SocketManager.getInstance().emit("voice:error", JSONObject().apply {
                    put("sessionId", sessionId)
                    put("message", "Error: ${e.message}")
                })
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                    audioRecord = null
                } catch (_: Exception) {}
            }
        }

        recordingThread?.start()
    }

    private fun stopRecording() {
        isRecording = false
        currentSessionId = null
        onStatusChange?.invoke(false, null)

        recordingThread?.interrupt()
        recordingThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (_: Exception) {}

        val notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifManager.cancel(NOTIF_ID)

        Log.d(TAG, "Voice recording stopped")
    }

    private fun sendAudioChunk(sessionId: String, pcmData: ByteArray) {
        if (!SocketManager.getInstance().isSocketConnected()) return

        try {
            // Encode PCM to Base64 for transmission
            val audioBase64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)

            SocketManager.getInstance().emit("voice:frame", JSONObject().apply {
                put("sessionId", sessionId)
                put("audioBase64", audioBase64)
                put("sequence", sequenceNumber++)
                put("duration", CHUNK_DURATION_MS)
                put("sampleRate", SAMPLE_RATE)
                put("timestamp", java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    java.util.Locale.US
                ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()))
            })

            Log.d(TAG, "Sent audio chunk #$sequenceNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk: ${e.message}")
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Monitor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi monitoring suara"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
