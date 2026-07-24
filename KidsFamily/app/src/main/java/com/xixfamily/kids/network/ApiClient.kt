package com.xixfamily.kids.network

import android.util.Log
import com.xixfamily.kids.utils.Config
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ApiClient {
    companion object {
        private const val TAG = "KidsApi"
        private var authToken: String = ""

        fun configure() {
            // Auto-configure from Config
        }

        fun configure(url: String) {
            // Kept for backward compatibility, but URL is now from Config
        }

        fun setToken(token: String) {
            authToken = token
        }

        fun register(email: String, password: String, name: String, familyCode: String): JSONObject? {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("name", name)
                put("role", "kid")
                put("familyCode", familyCode)
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
            return get("${Config.SERVER_URL}/api/auth/profile")
        }

        private fun get(urlString: String): JSONObject? {
            return httpRequest("GET", urlString, null)
        }

        private fun post(urlString: String, body: JSONObject): JSONObject? {
            return httpRequest("POST", urlString, body.toString())
        }

        private fun httpRequest(method: String, urlString: String, body: String?): JSONObject? {
            return try {
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.doInput = true

                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                }

                if (authToken.isNotEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer $authToken")
                }

                if (body != null) {
                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(body)
                    writer.flush()
                    writer.close()
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val reader = BufferedReader(InputStreamReader(stream))
                val response = reader.readText()
                reader.close()
                conn.disconnect()

                if (response.isNotEmpty()) JSONObject(response) else null
            } catch (e: Exception) {
                Log.e(TAG, "HTTP error: ${e.message}")
                null
            }
        }
    }
}
