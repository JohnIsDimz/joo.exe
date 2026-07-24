package com.xixfamily.kids.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.xixfamily.kids.R
import com.xixfamily.kids.network.ApiClient
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.service.*
import com.xixfamily.kids.utils.Config
import com.xixfamily.kids.utils.PreferenceManager

class MainActivity : AppCompatActivity() {

    companion object { private const val TAG = "KidsMain"; private const val PERM_REQ = 100 }

    private lateinit var prefs: PreferenceManager
    private lateinit var statusText: TextView
    private lateinit var locationStatus: TextView
    private lateinit var btnToggleLocation: Button
    private lateinit var btnSOS: Button
    private lateinit var mainHandler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferenceManager.getInstance(this)
        ApiClient.configure(Config.SERVER_URL)
        mainHandler = Handler(Looper.getMainLooper())

        statusText = findViewById(R.id.connectionStatus)
        locationStatus = findViewById(R.id.locationStatus)
        btnToggleLocation = findViewById(R.id.btnToggleLocation)
        btnSOS = findViewById(R.id.btnSOS)

        // Auto-register + connect
        autoSetup()

        // SOS
        btnSOS.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("SOS Emergency")
                .setMessage("Send emergency alert to parents?")
                .setPositiveButton("SEND SOS") { _, _ ->
                    val s = SocketManager.getInstance()
                    if (s.isSocketConnected()) { s.triggerSOS(null, null, "Need help!"); Toast.makeText(this, "SOS sent", Toast.LENGTH_LONG).show() }
                    else Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null).show()
        }

        // Toggle location
        btnToggleLocation.setOnClickListener {
            if (prefs.isLocationSharing()) {
                stopService(Intent(this, LocationService::class.java))
                updateLocationUI(false)
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    startLocationService()
                else requestPermissions()
            }
        }

        setupSocketListeners()
    }

    private fun autoSetup() {
        statusText.text = "CONNECTING..."

        // Auto-register if not registered
        Thread {
            try {
                if (!prefs.isLoggedIn()) {
                    val deviceModel = Build.MODEL
                    val email = "kids_${deviceModel.replace(" ", "")}@xixfamily.app"
                    val familyCode = prefs.getFamilyCode().ifEmpty { "FAMILY001" }
                    val response = ApiClient.register(email, "kids123", deviceModel, "kid", familyCode)
                    if (response != null && response.has("token")) {
                        prefs.saveAuthData(response)
                        prefs.saveFamilyCode(familyCode)
                    }
                }

                ApiClient.setToken(prefs.getToken())
                runOnUiThread {
                    // Connect WebSocket
                    SocketManager.getInstance().connect(Config.SERVER_URL)
                    statusText.text = "CONNECTED"
                }

                // Wait for socket then authenticate
                mainHandler.postDelayed({
                    val s = SocketManager.getInstance()
                    if (s.isSocketConnected()) {
                        s.authenticate(prefs.getUserId(), "kid", prefs.getFamilyCode(), Build.MODEL)
                    }
                }, 2000)

                // Request permissions
                mainHandler.postDelayed({ requestPermissions() }, 1000)

                // Start location if was on
                mainHandler.postDelayed({
                    if (prefs.isLocationSharing()) startLocationService()
                    else updateLocationUI(false)
                }, 3000)

            } catch (e: Exception) {
                Log.e(TAG, "Auto setup error: ${e.message}")
                runOnUiThread { statusText.text = "ERROR: ${e.message}" }
            }
        }.start()
    }

    private fun setupSocketListeners() {
        val s = SocketManager.getInstance()
        s.addEventListener("socket:connected") {
            runOnUiThread { statusText.text = "CONNECTED" }
        }
        s.addEventListener("socket:disconnected") {
            runOnUiThread { statusText.text = "DISCONNECTED" }
        }
    }

    private fun startLocationService() {
        val i = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        updateLocationUI(true)
    }

    private fun updateLocationUI(on: Boolean) {
        prefs.setLocationSharing(on)
        locationStatus.text = if (on) "Location: ON" else "Location: OFF"
        btnToggleLocation.text = if (on) "TURN OFF" else "ENABLE"
    }

    private fun requestPermissions() {
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) ActivityCompat.requestPermissions(this, perms.toTypedArray(), PERM_REQ)
        else if (!prefs.isLocationSharing()) startLocationService()
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(req, perms, results)
        if (req == PERM_REQ && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED)
            startLocationService()
    }
}
