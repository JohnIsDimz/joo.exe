package com.xixfamily.parent.ui.dashboard

import android.os.Bundle
import android.graphics.Color
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.xixfamily.parent.R
import com.xixfamily.parent.network.ApiClient
import com.xixfamily.parent.network.SocketManager
import com.xixfamily.parent.ui.monitoring.MultiChildFragment
import com.xixfamily.parent.utils.Config
import com.xixfamily.parent.utils.PreferenceManager

class MainActivity : AppCompatActivity() {

    private lateinit var connectionStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectionStatus = findViewById(R.id.connectionStatus)

        // Load device list as default
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, MultiChildFragment())
            .commit()

        // Auto-connect to server
        autoConnect()
    }

    private fun autoConnect() {
        Thread {
            try {
                val prefs = PreferenceManager.getInstance(this)

                if (prefs.isLoggedIn()) {
                    ApiClient.setToken(prefs.getToken())
                    runOnUiThread { connectWebSocket(prefs) }
                    return@Thread
                }

                // Auto-register if not logged in
                val deviceId = "parent_" + java.util.UUID.randomUUID().toString().take(8)
                val email = "$deviceId@xixfamily.app"
                val response = ApiClient.register(email, "parent123", "Parent", "parent", null)

                if (response != null && response.has("token")) {
                    prefs.saveAuthResponse(response)
                    ApiClient.setToken(prefs.getToken())
                    runOnUiThread { connectWebSocket(prefs) }
                } else {
                    // Try login
                    val loginResponse = ApiClient.login(email, "parent123")
                    if (loginResponse != null && loginResponse.has("token")) {
                        prefs.saveAuthResponse(loginResponse)
                        ApiClient.setToken(prefs.getToken())
                        runOnUiThread { connectWebSocket(prefs) }
                    } else {
                        runOnUiThread {
                            connectionStatus.text = "SYS:OFFLINE"
                            connectionStatus.setTextColor(Color.parseColor("#404060")); connectionStatus.text = "SYS:OFFLINE"
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    connectionStatus.text = "SYS:OFFLINE"
                    connectionStatus.setTextColor(Color.parseColor("#404060")); connectionStatus.text = "SYS:OFFLINE"
                }
            }
        }.start()
    }

    private fun connectWebSocket(prefs: PreferenceManager) {
        val socket = SocketManager.getInstance()
        socket.connect(Config.SERVER_URL, prefs.getToken())

        socket.addEventListener("socket:connected") {
            runOnUiThread {
                connectionStatus.text = "SYSTEM:ONLINE"
                connectionStatus.setTextColor(Color.parseColor("#00FFF5")); connectionStatus.text = "SYSTEM:ONLINE"
                socket.authenticate(
                    prefs.getUserId(), prefs.getUserRole(),
                    prefs.getFamilyCode(), prefs.getUserName()
                )
            }
        }

        socket.addEventListener("socket:disconnected") {
            runOnUiThread {
                connectionStatus.text = "SYS:OFFLINE"
                connectionStatus.setTextColor(Color.parseColor("#404060")); connectionStatus.text = "SYS:OFFLINE"
            }
        }
    }
}
