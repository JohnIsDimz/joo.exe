package com.xixfamily.parent.network

import android.util.Log
import com.xixfamily.parent.utils.Config
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ApiClient {
    companion object {
        private const val TAG = "ApiClient"
        private var authToken: String = ""

        fun configure(@Suppress("UNUSED_PARAMETER") url: String) {
            // Kept for backward compatibility, URL is now hardcoded in Config
        }

        fun setToken(token: String) {
            authToken = token
        }

        fun getToken(): String = authToken

        // Auth
        fun register(email: String, password: String, name: String, role: String, familyCode: String? = null): JSONObject? {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("name", name)
                put("role", role)
                if (familyCode != null) put("familyCode", familyCode)
            }
            return post("${Config.SERVER_URL}/api/auth/register", body)
        }

        fun login(email: String, password: String): JSONObject? {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }
            return post("${Config.SERVER_URL}/api/auth/login", body)
        }

        fun getProfile(): JSONObject? {
            return get("${Config.SERVER_URL}/api/auth/profile", authHeader = true)
        }

        fun joinFamily(familyCode: String): JSONObject? {
            val body = JSONObject().apply {
                put("familyCode", familyCode)
            }
            return post("${Config.SERVER_URL}/api/auth/join-family", body, authHeader = true)
        }

        // Family
        fun getFamilyMembers(): JSONObject? {
            return get("${Config.SERVER_URL}/api/family/members", authHeader = true)
        }

        // SOS
        fun getSOSAlerts(): JSONObject? {
            return get("${Config.SERVER_URL}/api/sos", authHeader = true)
        }

        fun resolveSOSAlert(alertId: String): JSONObject? {
            return post("${Config.SERVER_URL}/api/sos/${alertId}/resolve", JSONObject(), authHeader = true)
        }

        // Locations
        fun getLocationHistory(userId: String, limit: Int = 50): JSONObject? {
            return get("${Config.SERVER_URL}/api/location/${userId}?limit=${limit}", authHeader = true)
        }

        fun getLatestLocation(userId: String): JSONObject? {
            return get("${Config.SERVER_URL}/api/location/${userId}/latest", authHeader = true)
        }

        // App Usage
        fun getAppUsage(userId: String, date: String? = null): JSONObject? {
            val url = if (date != null) {
                "${Config.SERVER_URL}/api/app-usage/${userId}?date=${date}"
            } else {
                "${Config.SERVER_URL}/api/app-usage/${userId}"
            }
            return get(url, authHeader = true)
        }

        // Screen Time
        fun setScreenTimeLimit(kidId: String, dailyLimitMinutes: Int): JSONObject? {
            val body = JSONObject().apply {
                put("kidId", kidId)
                put("dailyLimitMinutes", dailyLimitMinutes)
            }
            return post("${Config.SERVER_URL}/api/screen-time", body, authHeader = true)
        }

        fun getScreenTimeLimits(): JSONObject? {
            return get("${Config.SERVER_URL}/api/screen-time", authHeader = true)
        }

        // Check-ins
        fun getCheckIns(): JSONObject? {
            return get("${Config.SERVER_URL}/api/checkins", authHeader = true)
        }

        // Geofences
        fun getGeofences(): JSONObject? {
            return get("${Config.SERVER_URL}/api/geofence", authHeader = true)
        }

        fun createGeofence(name: String, latitude: Double, longitude: Double, radius: Int = 100): JSONObject? {
            val body = JSONObject().apply {
                put("name", name)
                put("latitude", latitude)
                put("longitude", longitude)
                put("radius", radius)
            }
            return post("${Config.SERVER_URL}/api/geofence", body, authHeader = true)
        }

        // SMS / Chat Logs
        fun getSmsLogs(userId: String, limit: Int = 50): JSONObject? {
            return get("${Config.SERVER_URL}/api/sms/${userId}?limit=${limit}", authHeader = true)
        }

        fun getNotificationEvents(userId: String, limit: Int = 50): JSONObject? {
            return get("${Config.SERVER_URL}/api/notifications-events/${userId}?limit=${limit}", authHeader = true)
        }

        // Voice Sessions
        fun getVoiceSessions(userId: String): JSONObject? {
            return get("${Config.SERVER_URL}/api/voice-sessions/${userId}", authHeader = true)
        }

        // Notifications
        fun getNotifications(): JSONObject? {
            return get("${Config.SERVER_URL}/api/notifications", authHeader = true)
        }

        fun markNotificationRead(notificationId: String): JSONObject? {
            return post("${Config.SERVER_URL}/api/notifications/${notificationId}/read", JSONObject(), authHeader = true)
        }

        // HTTP Methods
        private fun get(urlString: String, authHeader: Boolean = false): JSONObject? {
            return httpRequest("GET", urlString, null, authHeader)
        }

        private fun post(urlString: String, body: JSONObject, authHeader: Boolean = false): JSONObject? {
            return httpRequest("POST", urlString, body.toString(), authHeader)
        }

        private fun httpRequest(method: String, urlString: String, body: String?, authHeader: Boolean): JSONObject? {
            return try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doInput = true

                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                }

                if (authHeader && authToken.isNotEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer $authToken")
                }

                if (body != null) {
                    val writer = OutputStreamWriter(connection.outputStream)
                    writer.write(body)
                    writer.flush()
                    writer.close()
                }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val reader = BufferedReader(InputStreamReader(stream))
                val response = reader.readText()
                reader.close()
                connection.disconnect()

                if (response.isNotEmpty()) {
                    JSONObject(response)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "HTTP $method error: ${e.message}")
                null
            }
        }
    }
}
