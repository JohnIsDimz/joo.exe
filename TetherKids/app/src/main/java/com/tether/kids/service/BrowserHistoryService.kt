package com.tether.kids.service

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Browser
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tether.kids.network.SocketManager
import com.tether.kids.ui.main.MainActivity
import org.json.JSONArray
import org.json.JSONObject

/**
 * BrowserHistoryService — Real implementation.
 *
 * Strategy:
 *  1. Try Browser.Bookmarks URI (legacy, butuh permission READ_HISTORY_BOOKMARKS)
 *  2. Try Chrome's content provider (no permission needed in most cases)
 *  3. Try other browsers (Samsung Internet, Firefox, etc.)
 *  4. Fallback: UsageStatsManager untuk detect browser app yang dibuka
 *
 * Returns data via 'browser:history' socket event.
 */
class BrowserHistoryService : Service() {
    companion object {
        private const val TAG = "BrowserHist"
        private const val NID = 2020
        private const val CID = "tetherkids_browser"
        private const val MAX = 100

        // Browser package names for UsageStats fallback
        private val BROWSER_PACKAGES = listOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "com.sec.android.app.sbrowser",
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.brave.browser",
            "com.UCMobile.intl",
            "com.microsoft.emmx",
            "com.duckduckgo.mobile.android",
            "com.bromite.android",
            "org.torproject.torbrowser",
            "com.kiwibrowser.browser"
        )

        // Content URIs to try
        private val URIS = listOf(
            Browser.BOOKMARKS_URI,
            Uri.parse("content://com.android.chrome.browser/history"),
            Uri.parse("content://com.android.chrome.browser/bookmarks"),
            Uri.parse("content://com.android.chrome.browser/provider_books/history"),
            Uri.parse("content://com.sec.android.app.sbrowser.browser/history"),
            Uri.parse("content://com.sec.android.app.sbrowser.browser/bookmarks"),
            Uri.parse("content://org.mozilla.firefox.browser/history"),
            Uri.parse("content://org.mozilla.firefox.db.browser/history"),
            Uri.parse("content://com.opera.browser/history")
        )
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NID, notif("Reading browser history..."))
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        when (i?.action) {
            "STOP" -> { stopSelf(); return START_NOT_STICKY }
            else -> readHistory()
        }
        return START_NOT_STICKY
    }

    private fun readHistory() {
        val all = JSONArray()
        val sourcesUsed = mutableListOf<String>()

        // Method 1: Try each ContentProvider URI
        for (uri in URIS) {
            try {
                val beforeCount = all.length()
                readFromUri(uri, all)
                if (all.length() > beforeCount) {
                    sourcesUsed.add(uri.authority ?: "unknown")
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission denied for ${uri.authority}")
            } catch (e: Exception) {
                Log.w(TAG, "URI ${uri.authority} failed: ${e.message}")
            }
        }

        // Method 2: UsageStats fallback to find browser app usage
        try {
            val browserUsage = getBrowserUsageFromUsageStats()
            if (browserUsage.length() > 0) {
                all.put(JSONObject().apply {
                    put("type", "usage_stats")
                    put("title", "Browser Apps Used")
                    put("url", "")
                    put("browsers", browserUsage)
                    put("date", System.currentTimeMillis())
                })
                sourcesUsed.add("usage_stats")
            }
        } catch (e: Exception) {
            Log.w(TAG, "UsageStats fallback failed: ${e.message}")
        }

        // Send results
        if (SocketManager.getInstance().isSocketConnected()) {
            val payload = JSONObject().apply {
                put("history", all)
                put("count", all.length())
                put("sources", JSONArray(sourcesUsed))
                put("timestamp", System.currentTimeMillis())
            }
            SocketManager.getInstance().emit("browser:history", payload)
            Log.d(TAG, "Sent ${all.length()} entries from ${sourcesUsed.size} sources: $sourcesUsed")
        }

        stopSelf()
    }

    private fun readFromUri(uri: Uri, out: JSONArray) {
        val projection = try {
            arrayOf(
                Browser.BookmarkColumns.TITLE,
                Browser.BookmarkColumns.URL,
                Browser.BookmarkColumns.DATE,
                Browser.BookmarkColumns.BOOKMARK,
                Browser.BookmarkColumns.VISITS
            )
        } catch (e: Exception) {
            // Different column names in other browsers
            arrayOf("title", "url", "date", "bookmark", "visits", "_id")
        }

        var c: Cursor? = null
        try {
            c = contentResolver.query(uri, projection, null, null, "${Browser.BookmarkColumns.DATE} DESC LIMIT $MAX")
                ?: return

            val colTitle = c.getColumnIndex(Browser.BookmarkColumns.TITLE)
            val colUrl = c.getColumnIndex(Browser.BookmarkColumns.URL)
            val colDate = c.getColumnIndex(Browser.BookmarkColumns.DATE)
            val colBookmark = c.getColumnIndex(Browser.BookmarkColumns.BOOKMARK)
            val colVisits = c.getColumnIndex(Browser.BookmarkColumns.VISITS)

            while (c.moveToNext()) {
                val url = if (colUrl >= 0) c.getString(colUrl) ?: "" else ""
                if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) continue

                out.put(JSONObject().apply {
                    put("type", "history")
                    put("browser", uri.authority ?: "default")
                    put("title", if (colTitle >= 0) c.getString(colTitle) ?: "" else "")
                    put("url", url)
                    put("date", if (colDate >= 0) c.getLong(colDate) else 0L)
                    put("isBookmark", colBookmark >= 0 && c.getInt(colBookmark) == 1)
                    put("visits", if (colVisits >= 0) c.getInt(colVisits) else 0)
                })
            }
        } finally {
            c?.close()
        }
    }

    @Suppress("MissingPermission")
    private fun getBrowserUsageFromUsageStats(): JSONArray {
        val out = JSONArray()
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager?
                ?: return out

            val now = System.currentTimeMillis()
            val begin = now - (7 * 24 * 60 * 60 * 1000L) // 7 days back

            val events = usm.queryEvents(begin, now)
            val event = UsageEvents.Event()
            val browserSessions = mutableMapOf<String, Long>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    val pkg = event.packageName ?: continue
                    if (pkg in BROWSER_PACKAGES) {
                        browserSessions[pkg] = (browserSessions[pkg] ?: 0L) + 1
                    }
                }
            }

            for ((pkg, count) in browserSessions) {
                out.put(JSONObject().apply {
                    put("package", pkg)
                    put("launchCount", count)
                })
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "UsageStats permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "UsageStats query failed: ${e.message}")
        }
        return out
    }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { super.onDestroy() }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel(CID, "Browser History", NotificationManager.IMPORTANCE_LOW)
            c.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }

    private fun notif(t: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CID)
            .setContentTitle("Tether Kids — Browser")
            .setContentText(t)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
