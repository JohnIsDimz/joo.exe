package com.xixfamily.kids.service

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.os.*
import android.util.Base64
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.R
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.ui.main.MainActivity
import java.io.ByteArrayOutputStream

@Suppress("DEPRECATION")
class CameraService : Service() {

    companion object {
        private const val TAG = "CameraService"
        private const val NOTIFICATION_ID = 4001
        private const val CHANNEL_ID = "kids_camera"
        private const val FRAME_INTERVAL_MS = 2000L // 2 seconds between frames

        var isCctvActive = false
            private set
        private var instance: CameraService? = null
        fun getInstance(): CameraService? = instance
    }

    private var camera: Camera? = null
    private var surfaceView: SurfaceView? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var frameHandler: Handler? = null
    private var frameRunnable: Runnable? = null
    private var useFrontCamera = true

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("CCTV aktif"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_CCTV" -> {
                useFrontCamera = intent.getBooleanExtra("useFrontCamera", true)
                startCCTV()
            }
            "STOP_CCTV" -> stopCCTV()
            "SWITCH_CAMERA" -> switchCamera()
        }
        return START_STICKY
    }

    private fun startCCTV() {
        if (isCctvActive) return
        isCctvActive = true

        try {
            val cameraId = getCameraId(useFrontCamera)
            camera = Camera.open(cameraId)
            val params = camera?.parameters
            params?.apply {
                setPreviewSize(640, 480)
                setPictureSize(640, 480)
                jpegQuality = 60
                focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            }
            camera?.parameters = params
            camera?.setDisplayOrientation(90)

            // Use a dummy SurfaceView for camera preview
            surfaceView = SurfaceView(this)
            surfaceHolder = surfaceView?.holder
            surfaceHolder?.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
            surfaceHolder?.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    try {
                        camera?.setPreviewDisplay(holder)
                        camera?.startPreview()
                        Log.d(TAG, "Camera preview started")
                        startFrameCapture()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting preview: ${e.message}")
                    }
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {}
            })

            // Trigger surface creation
            surfaceView?.visibility = 0 // View.VISIBLE but off-screen

            Log.d(TAG, "CCTV started (front: $useFrontCamera)")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting camera: ${e.message}")
            isCctvActive = false
        }
    }

    private fun startFrameCapture() {
        frameHandler = Handler(Looper.getMainLooper())
        frameRunnable = Runnable {
            if (!isCctvActive) return@Runnable

            try {
                camera?.takePicture(null, null, { data, _ ->
                    // Send the JPEG frame via WebSocket
                    sendFrameViaSocket(data)

                    // Restart preview for next frame
                    camera?.startPreview()

                    // Schedule next frame
                    frameHandler?.postDelayed(frameRunnable!!, FRAME_INTERVAL_MS)
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error taking picture: ${e.message}")
                frameHandler?.postDelayed(frameRunnable!!, FRAME_INTERVAL_MS)
            }
        }

        // Start first frame after a brief delay
        frameHandler?.postDelayed(frameRunnable!!, 1000)
    }

    private fun sendFrameViaSocket(jpegData: ByteArray) {
        val base64 = Base64.encodeToString(jpegData, Base64.NO_WRAP)

        val socket = SocketManager.getInstance()
        if (socket.isSocketConnected()) {
            socket.emit("camera:frame", org.json.JSONObject().apply {
                put("imageBase64", base64)
                put("isFrontCamera", useFrontCamera)
                put("timestamp", java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    java.util.Locale.US
                ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()))
            })
        }
    }

    private fun switchCamera() {
        useFrontCamera = !useFrontCamera
        stopCCTVInternal()
        startCCTV()
    }

    private fun stopCCTV() {
        stopCCTVInternal()
        isCctvActive = false

        val socket = SocketManager.getInstance()
        if (socket.isSocketConnected()) {
            socket.emit("camera:stopped", org.json.JSONObject())
        }

        Log.d(TAG, "CCTV stopped")
    }

    private fun stopCCTVInternal() {
        try {
            frameHandler?.removeCallbacksAndMessages(null)
            camera?.stopPreview()
            camera?.release()
            camera = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera: ${e.message}")
        }
    }

    private fun getCameraId(frontFacing: Boolean): Int {
        var cameraId = 0
        try {
            val numberOfCameras = Camera.getNumberOfCameras()
            val cameraInfo = Camera.CameraInfo()
            for (i in 0 until numberOfCameras) {
                Camera.getCameraInfo(i, cameraInfo)
                if (cameraInfo.facing == (if (frontFacing) Camera.CameraInfo.CAMERA_FACING_FRONT
                    else Camera.CameraInfo.CAMERA_FACING_BACK)
                ) {
                    cameraId = i
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting camera ID: ${e.message}")
        }
        return cameraId
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "CCTV Mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Camera is active"
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
            .setContentTitle("KidsFamily CCTV")
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
        stopCCTVInternal()
        isCctvActive = false
        super.onDestroy()
    }
}
