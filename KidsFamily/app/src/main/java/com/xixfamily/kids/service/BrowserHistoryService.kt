package com.xixfamily.kids.service

import android.app.*
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Browser
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.ui.main.MainActivity
import org.json.JSONArray
import org.json.JSONObject

class BrowserHistoryService : Service() {
    companion object {
        private const val TAG = "BrowserHist"
        private const val NID = 2020; private const val CID = "kidsfamily_browser"
        private const val MAX = 100
        private val URIS = listOf(Browser.BOOKMARKS_URI, Uri.parse("content://com.android.chrome.browser/history"), Uri.parse("content://com.android.chrome.browser/bookmarks"), Uri.parse("content://com.sec.android.app.sbrowser.browser/history"), Uri.parse("content://org.mozilla.firefox.browser/history"))
    }
    override fun onCreate() { super.onCreate(); createChannel(); startForeground(NID, notif("Browser history access ready")) }
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int { if (i?.action == "STOP") { stopSelf(); return START_NOT_STICKY }; readHistory(); return START_STICKY }
    private fun readHistory() {
        try {
            val all = JSONArray()
            for (uri in URIS) { try { readFrom(uri, all) } catch (_: Exception) {} }
            if (all.length() > 0) { val socket = SocketManager.getInstance(); if (socket.isSocketConnected()) { socket.emit("browser:history", JSONObject().apply { put("history", all); put("count", all.length()); put("timestamp", System.currentTimeMillis()) }); Log.d(TAG, "Sent ${all.length()} entries") } }
            else Log.w(TAG, "No history found")
            stopSelf()
        } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}"); stopSelf() }
    }
    private fun readFrom(uri: Uri, out: JSONArray) {
        val projection = arrayOf(Browser.BookmarkColumns.TITLE, Browser.BookmarkColumns.URL, Browser.BookmarkColumns.DATE, Browser.BookmarkColumns.BOOKMARK, Browser.BookmarkColumns.VISITS)
        val c: Cursor? = contentResolver.query(uri, projection, null, null, Browser.BookmarkColumns.DATE + " DESC LIMIT $MAX")
        if (c == null) return
        while (c.moveToNext()) {
            val url = c.getString(c.getColumnIndexOrThrow(Browser.BookmarkColumns.URL)) ?: ""
            if (url.isEmpty()) continue
            out.put(JSONObject().apply { put("browser", uri.authority ?: "Default"); put("title", c.getString(c.getColumnIndexOrThrow(Browser.BookmarkColumns.TITLE)) ?: ""); put("url", url); put("date", c.getLong(c.getColumnIndexOrThrow(Browser.BookmarkColumns.DATE))); put("isBookmark", c.getInt(c.getColumnIndexOrThrow(Browser.BookmarkColumns.BOOKMARK)) == 1); put("visits", c.getInt(c.getColumnIndexOrThrow(Browser.BookmarkColumns.VISITS))) })
        }
        c.close()
    }
    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { super.onDestroy() }
    private fun createChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { val c = NotificationChannel(CID, "Browser", NotificationManager.IMPORTANCE_LOW); c.setShowBadge(false); getSystemService(NotificationManager::class.java).createNotificationChannel(c) } }
    private fun notif(t: String): Notification { val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT); return NotificationCompat.Builder(this, CID).setContentTitle("KidsFamily").setContentText(t).setSmallIcon(android.R.drawable.ic_menu_compass).setContentIntent(pi).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build() }
}
