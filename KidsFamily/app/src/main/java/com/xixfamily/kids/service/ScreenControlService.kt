package com.xixfamily.kids.service

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.R
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.ui.main.MainActivity
import com.xixfamily.kids.utils.PreferenceManager
import java.io.ByteArrayOutputStream

class ScreenControlService : Service() {

    companion object {
        private const val TAG = "ScreenControl"
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "kids_screen_control"
        private const val CAPTURE_NOTIFICATION_ID = 3002

        var mediaProjection: MediaProjection? = null
        var mediaProjectionIntent: Intent? = null
        var mediaProjectionResultCode: Int = 0
        var isCapturing = false
        var isScreenLocked = false
            private set
        var isViewingScreen = false
            private set

        private var instance: ScreenControlService? = null
        fun getInstance(): ScreenControlService? = instance
    }

    private lateinit var prefs: PreferenceManager
    private lateinit var windowManager: WindowManager
    private var lockOverlayView: View? = null
    private var captureCallback: ((Bitmap) -> Unit)? = null
    private var viewInterval: Long = 0
    private var viewHandler: Handler? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = PreferenceManager.getInstance(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Screen control ready"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "LOCK" -> {
                val reason = intent.getStringExtra("reason") ?: "Screen time exceeded"
                showLockOverlay(reason)
            }
            "UNLOCK" -> removeLockOverlay()
            "CAPTURE" -> captureScreenshot()
            "VIEW_START" -> {
                viewInterval = intent.getLongExtra("interval", 5000)
                startScreenView()
            }
            "VIEW_STOP" -> stopScreenView()
        }
        return START_STICKY
    }

    // ============ SCREENSHOT CAPTURE ============

    fun captureScreenshot(callback: ((Bitmap) -> Unit)? = null) {
        captureCallback = callback

        if (mediaProjection == null && mediaProjectionIntent != null) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = manager.getMediaProjection(
                mediaProjectionResultCode,
                mediaProjectionIntent!!
            )
        }

        val projection = mediaProjection
        if (projection == null) {
            Log.e(TAG, "MediaProjection not initialized")
            return
        }

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val imageReader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )

        projection.createVirtualDisplay(
            "ScreenCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        ).apply {
            val handler = Handler(Looper.getMainLooper())

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * metrics.widthPixels

                    val bitmap = Bitmap.createBitmap(
                        metrics.widthPixels + rowPadding / pixelStride,
                        metrics.heightPixels,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    // Crop to actual size
                    val cropped = Bitmap.createBitmap(
                        bitmap, 0, 0,
                        metrics.widthPixels, metrics.heightPixels
                    )
                    bitmap.recycle()
                    image.close()

                    // Send via WebSocket
                    sendScreenshotViaSocket(cropped)

                    callback?.invoke(cropped)
                }
                release()
                reader.close()
            }, handler)

            handler.postDelayed({
                // Timeout: release after 1 second
                try {
                    release()
                    reader.close()
                } catch (e: Exception) {}
            }, 1000)
        }
    }

    private fun sendScreenshotViaSocket(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val byteArray = stream.toByteArray()
        val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)

        val socket = SocketManager.getInstance()
        if (socket.isSocketConnected()) {
            socket.emit("screen:capture:result", org.json.JSONObject().apply {
                put("requestId", java.util.UUID.randomUUID().toString())
                put("imageBase64", base64)
                put("timestamp", java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    java.util.Locale.US
                ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()))
            })
        }

        bitmap.recycle()
        Log.d(TAG, "Screenshot sent (${base64.length} bytes base64)")
    }

    // ============ SCREEN VIEW (REAL-TIME) ============

    private fun startScreenView() {
        if (isViewingScreen) return
        isViewingScreen = true
        isCapturing = true

        viewHandler = Handler(Looper.getMainLooper())
        scheduleViewFrame()
    }

    private fun scheduleViewFrame() {
        if (!isViewingScreen) return

        captureScreenshot { bitmap ->
            if (!isViewingScreen) return@captureScreenshot

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 40, stream)
            val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

            val socket = SocketManager.getInstance()
            if (socket.isSocketConnected()) {
                socket.emit("screen:view:frame", org.json.JSONObject().apply {
                    put("imageBase64", base64)
                    put("timestamp", java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        java.util.Locale.US
                    ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()))
                })
            }

            // Schedule next frame
            viewHandler?.postDelayed({ scheduleViewFrame() }, viewInterval)
        }
    }

    private fun stopScreenView() {
        isViewingScreen = false
        isCapturing = false
        viewHandler?.removeCallbacksAndMessages(null)

        val socket = SocketManager.getInstance()
        if (socket.isSocketConnected()) {
            socket.emit("screen:view:stop", org.json.JSONObject())
        }
    }

    // ============ LOCK SCREEN OVERLAY ============

    private fun showLockOverlay(reason: String) {
        if (isScreenLocked) return
        isScreenLocked = true

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        lockOverlayView = inflater.inflate(R.layout.overlay_screen_lock, null)

        val lockReason = lockOverlayView?.findViewById<TextView>(R.id.lockReason)
        val lockMessage = lockOverlayView?.findViewById<TextView>(R.id.lockMessage)
        val btnUnlock = lockOverlayView?.findViewById<Button>(R.id.btnOverlayUnlock)

        lockReason?.text = reason

        btnUnlock?.setOnClickListener {
            // Actually send unlock request to parents via SOS-like notification
            val socket = SocketManager.getInstance()
            if (socket.isSocketConnected()) {
                socket.emit("checkin", org.json.JSONObject().apply {
                    put("status", "help")
                    put("message", "Please unlock my device!")
                })
            }
        }

        try {
            windowManager.addView(lockOverlayView, params)
            Log.d(TAG, "Lock overlay shown: $reason")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay: ${e.message}")
        }
    }

    private fun removeLockOverlay() {
        lockOverlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay: ${e.message}")
            }
        }
        lockOverlayView = null
        isScreenLocked = false
    }

    // ============ NOTIFICATION ============

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Screen Control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screen control service"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KidsFamily Screen Control")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        stopScreenView()
        removeLockOverlay()
        super.onDestroy()
    }
}
