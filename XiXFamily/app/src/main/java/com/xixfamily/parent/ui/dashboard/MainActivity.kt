package com.xixfamily.parent.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
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
        connectionStatus.text = "SYS:OFFLINE"
        connectionStatus.setTextColor(Color.parseColor("#404060"))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MultiChildFragment())
                .commit()
        }

        autoConnect()
    }

    private fun autoConnect() {
        Thread {
            try {
                val prefs = PreferenceManager.getInstance(this)
                val email = "parent_" + java.util.UUID.randomUUID().toString().take(8) + "@xix.app"

                if (!prefs.isLoggedIn()) {
                    val resp = ApiClient.register(email, "parent123", "Parent", "parent", null)
                    if (resp != null && resp.has("token")) {
                        prefs.saveAuthResponse(resp)
                        ApiClient.setToken(prefs.getToken())
                    }
                } else {
                    ApiClient.setToken(prefs.getToken())
                }

                if (prefs.isLoggedIn()) {
                    runOnUiThread { connectWs(prefs) }
                } else {
                    val resp2 = ApiClient.login(email, "parent123")
                    if (resp2 != null && resp2.has("token")) {
                        prefs.saveAuthResponse(resp2)
                        ApiClient.setToken(prefs.getToken())
                        runOnUiThread { connectWs(prefs) }
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun connectWs(prefs: PreferenceManager) {
        val sk = SocketManager.getInstance()
        sk.connect(Config.SERVER_URL, prefs.getToken())

        sk.addEventListener("socket:connected") {
            runOnUiThread {
                connectionStatus.text = "SYS:ONLINE"
                connectionStatus.setTextColor(Color.parseColor("#00FFF5"))
            }
            sk.authenticate(prefs.getUserId(), prefs.getUserRole(), prefs.getFamilyCode(), prefs.getUserName())
        }

        sk.addEventListener("socket:disconnected") {
            runOnUiThread {
                connectionStatus.text = "SYS:OFFLINE"
                connectionStatus.setTextColor(Color.parseColor("#404060"))
            }
        }
    }
}
