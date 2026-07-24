package com.xixfamily.parent.network

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

    // Event listeners
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
                emitEvent("socket:connected", JSONObject().apply {
                    put("status", "connected")
                })
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                isConnected = false
                Log.d(TAG, "Disconnected from server")
                emitEvent("socket:disconnected", JSONObject().apply {
                    put("status", "disconnected")
                })
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Connection error: ${args.firstOrNull()}")
                emitEvent("socket:error", JSONObject().apply {
                    put("error", args.firstOrNull()?.toString() ?: "Unknown error")
                })
            }

            // Register all event handlers
            socket?.on("location:updated", onLocationUpdated)
            socket?.on("sos:alert", onSOSAlert)
            socket?.on("app:usage:updated", onAppUsageUpdated)
            socket?.on("checkin:received", onCheckInReceived)
            socket?.on("geofence:breach", onGeofenceBreach)
            socket?.on("notification", onNotification)
            socket?.on("user:online", onUserOnline)
            socket?.on("user:offline", onUserOffline)

            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting socket: ${e.message}")
        }
    }

    fun authenticate(userId: String, role: String, familyCode: String, name: String) {
        val data = JSONObject().apply {
            put("userId", userId)
            put("role", role)
            put("familyCode", familyCode)
            put("name", name)
        }
        emit("auth", data)
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
            listener(data)
        }
    }

    fun isSocketConnected(): Boolean = isConnected

    // Socket event handlers
    private val onLocationUpdated = Emitter.Listener { args ->
        try {
            val data = args.firstOrNull() as? JSONObject
            if (data != null) emitEvent("location:updated", data)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing location update: ${e.message}")
        }
    }

    private val onSOSAlert = Emitter.Listener { args ->
        try {
            val data = args.firstOrNull() as? JSONObject
            if (data != null) emitEvent("sos:alert", data)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SOS alert: ${e.message}")
        }
    }

    private val onAppUsageUpdated = Emitter.Listener { args ->
        try {
            val data = args.firstOrNull() as? JSONObject
            if (data != null) emitEvent("app:usage:updated", data)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing app usage: ${e.message}")
        }
    }

    private val onCheckInReceived = Emitter.Listener { args ->
        try {
            val data = args.firstOrNull() as? JSONObject
            if (data != null) emitEvent("checkin:received", data)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing checkin: ${e.message}")
        }
    }

    private val onGeofenceBreach = Emitter.Listener { args ->
        try {
            val data = args.firstOrNull() as? JSONObject
            if (data != null) emitEvent("geofence:breach", data)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing geofence breach: ${e.message}")
        }
    }

    private val onNotification = Emitter.Listener { args ->
        try {
            val data = args.firstOrNull() as? JSONObject
            if (data != null) emitEvent("notification", data)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing notification: ${e.message}")
        }
    }

    private val onUserOnline = Emitter.Listener { args ->
        try {
            val data = args.firstOrNull() as? JSONObject
            if (data != null) emitEvent("user:online", data)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing user online: ${e.message}")
        }
    }

    private val onUserOffline = Emitter.Listener { args ->
        try {
            val data = args.firstOrNull() as? JSONObject
            if (data != null) emitEvent("user:offline", data)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing user offline: ${e.message}")
        }
    }
}
