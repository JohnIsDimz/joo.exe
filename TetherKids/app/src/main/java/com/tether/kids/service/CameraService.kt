package com.tether.kids.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.app.NotificationCompat
import com.tether.kids.R
import com.tether.kids.network.SocketManager
import com.tether.kids.ui.main.MainActivity
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * CameraService — Camera2 API implementation (no more deprecated Camera1).
 *
 * Changes from Camera1 to Camera2:
 *  - Use CameraManager (not Camera.open)
 *  - Use CameraDevice + CameraCaptureSession
 *  - Use ImageReader to capture JPEG frames
 *  - Background thread for camera operations
 *
 * Triggered via socket event "camera:start" (was CCTV, now renamed to CAMERA).
 * Default frame interval: 2000ms (2 detik per foto).
 * Default max duration: 60 detik (configurable via "duration" extra).
 */
class CameraService : Service() {

    companion object {
        private const val TAG = "CameraSvc"
        private const val NOTIFICATION_ID = 4001
        private const val CHANNEL_ID = "kids_camera"
        private const val FRAME_INTERVAL_MS = 2000L
        private const val DEFAULT_MAX_DURATION_MS = 60_000L

        var isCameraActive = false
            private set
        private var instance: CameraService? = null
        fun getInstance(): CameraService? = instance
    }

    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var captureSession: CameraCaptureSession? = null
    private var useFrontCamera = true
    private var frameRunnable: Runnable? = null
    private var startTimeMs: Long = 0
    private var maxDurationMs: Long = DEFAULT_MAX_DURATION_MS

    override fun onCreate() {
        super.onCreate()
        instance = this
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        createNotificationChannel()
        startBackgroundThread()
        startForeground(NOTIFICATION_ID, createNotification("Camera aktif"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_CAMERA" -> {
                useFrontCamera = intent.getBooleanExtra("useFrontCamera", true)
                maxDurationMs = intent.getLongExtra("duration", DEFAULT_MAX_DURATION_MS)
                startCamera()
            }
            "STOP_CAMERA" -> stopCamera()
            "SWITCH_CAMERA" -> {
                useFrontCamera = !useFrontCamera
                stopCameraInternal()
                startCamera()
            }
        }
        return START_STICKY
    }

    private fun startCamera() {
        if (isCameraActive) return
        isCameraActive = true
        startTimeMs = System.currentTimeMillis()

        try {
            val camMgr = cameraManager ?: run {
                Log.e(TAG, "CameraManager is null")
                isCameraActive = false
                return
            }
            val cameraId = pickCameraId(camMgr, useFrontCamera) ?: run {
                Log.e(TAG, "No suitable camera found")
                isCameraActive = false
                return
            }
            val characteristics = camMgr.getCameraCharacteristics(cameraId)
            val jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
            val previewSize = pickOptimalSize(jpegSizes, 640, 480) ?: Size(640, 480)

            imageReader = ImageReader.newInstance(
                previewSize.width, previewSize.height,
                ImageFormat.JPEG, 2
            )
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    sendFrameViaSocket(bytes, previewSize)
                } catch (e: Exception) {
                    Log.e(TAG, "Image process error: ${e.message}")
                } finally {
                    image.close()
                }
            }, backgroundHandler)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                camMgr.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        cameraDevice = device
                        createCaptureSession()
                    }
                    override fun onDisconnected(device: CameraDevice) {
                        device.close()
                        cameraDevice = null
                    }
                    override fun onError(device: CameraDevice, error: Int) {
                        device.close()
                        cameraDevice = null
                        Log.e(TAG, "Camera open error: $error")
                        stopCamera()
                    }
                }, backgroundHandler)
            } else {
                Log.e(TAG, "Camera2 requires API 23+")
                isCameraActive = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Camera start error: ${e.message}")
            isCameraActive = false
        }
    }

    private fun createCaptureSession() {
        val device = cameraDevice ?: return
        val reader = imageReader ?: return
        try {
            device.createCaptureSession(
                listOf(reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        startFrameLoop()
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session config failed")
                        stopCamera()
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "createCaptureSession: ${e.message}")
        }
    }

    private fun startFrameLoop() {
        captureFrame()
    }

    private fun captureFrame() {
        val session = captureSession ?: return
        val reader = imageReader ?: return
        val device = cameraDevice ?: return

        // Check max duration
        if (maxDurationMs > 0 && System.currentTimeMillis() - startTimeMs > maxDurationMs) {
            Log.i(TAG, "Max duration reached, stopping camera")
            stopCamera()
            return
        }

        try {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.JPEG_QUALITY, 60.toByte())
                set(CaptureRequest.JPEG_ORIENTATION, if (useFrontCamera) 270 else 90)
            }
            session.capture(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    // Schedule next frame
                    backgroundHandler?.postDelayed({
                        if (isCameraActive) captureFrame()
                    }, FRAME_INTERVAL_MS)
                }
                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure
                ) {
                    Log.e(TAG, "Capture failed: ${failure.reason}")
                    backgroundHandler?.postDelayed({
                        if (isCameraActive) captureFrame()
                    }, FRAME_INTERVAL_MS)
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "captureFrame error: ${e.message}")
            backgroundHandler?.postDelayed({
                if (isCameraActive) captureFrame()
            }, FRAME_INTERVAL_MS)
        }
    }

    private fun sendFrameViaSocket(jpegBytes: ByteArray, size: Size) {
        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val sk = SocketManager.getInstance()
        if (sk.isSocketConnected()) {
            sk.emit("camera:frame", JSONObject().apply {
                put("imageBase64", base64)
                put("isFrontCamera", useFrontCamera)
                put("width", size.width)
                put("height", size.height)
                put("timestamp", System.currentTimeMillis())
            })
            Log.d(TAG, "Frame sent (${base64.length} bytes base64)")
        }
    }

    private fun stopCamera() {
        isCameraActive = false
        stopCameraInternal()
        val sk = SocketManager.getInstance()
        if (sk.isSocketConnected()) {
            sk.emit("camera:stopped", JSONObject())
        }
        Log.d(TAG, "Camera stopped")
    }

    private fun stopCameraInternal() {
        try {
            captureSession?.close()
            cameraDevice?.close()
            imageReader?.close()
        } catch (e: Exception) {
            Log.e(TAG, "stopCameraInternal: ${e.message}")
        }
        captureSession = null
        cameraDevice = null
        imageReader = null
    }

    private fun pickCameraId(manager: CameraManager, frontFacing: Boolean): String? {
        return try {
            manager.cameraIdList.firstOrNull { id ->
                val c = manager.getCameraCharacteristics(id)
                val facing = c.get(CameraCharacteristics.LENS_FACING)
                if (frontFacing) facing == CameraCharacteristics.LENS_FACING_FRONT
                else facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "pickCameraId: ${e.message}")
            null
        }
    }

    private fun pickOptimalSize(sizes: Array<Size>, maxWidth: Int, maxHeight: Int): Size? {
        var best: Size? = null
        for (sz in sizes) {
            if (sz.width <= maxWidth && sz.height <= maxHeight) {
                if (best == null || sz.width * sz.height > best.width * best.height) {
                    best = sz
                }
            }
        }
        return best ?: sizes.firstOrNull()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBg").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCameraInternal()
        stopBackgroundThread()
        instance = null
        isCameraActive = false
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel(CHANNEL_ID, "Camera Mode", NotificationManager.IMPORTANCE_LOW)
            c.setShowBadge(false)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(c)
        }
    }

    private fun createNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tether Kids Camera")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
