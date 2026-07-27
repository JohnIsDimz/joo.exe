package com.tether.parent.ui.security

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.tether.parent.R
import com.tether.parent.network.SocketManager
import org.json.JSONObject
import java.security.SecureRandom

/**
 * DeviceSecurityFragment — Anti-uninstall control panel untuk parent.
 *
 * Fitur:
 *  1. HIDE LAUNCHER ICON — sembunyikan Tether Kids dari app drawer anak
 *  2. BLOCK UNINSTALL — block uninstall via Device Owner (perlu DO aktif)
 *  3. GENERATE PIN — generate 6-digit PIN yang valid 10 menit
 *  4. MONITOR PACKAGES — lihat app apa saja yang baru di-install/uninstall
 *
 * Dipakai saat parent mau prevent anak dari uninstall Tether Kids.
 */
class DeviceSecurityFragment : Fragment() {

    companion object {
        private const val ARG_ID = "device_id"
        private const val ARG_NAME = "device_name"

        fun newInstance(deviceId: String, deviceName: String): DeviceSecurityFragment {
            val args = Bundle().apply {
                putString(ARG_ID, deviceId)
                putString(ARG_NAME, deviceName)
            }
            return DeviceSecurityFragment().apply { arguments = args }
        }
    }

    private var deviceId = ""
    private var deviceNameArg = ""
    private var generatedPin: String? = null
    private var pinExpiryMs: Long = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        deviceId = arguments?.getString(ARG_ID) ?: ""
        deviceNameArg = arguments?.getString(ARG_NAME) ?: "Device"
        return inflater.inflate(R.layout.fragment_device_security, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.deviceName).text = deviceNameArg

        view.findViewById<Button>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // === HIDE ICON ===
        view.findViewById<Button>(R.id.btnHideIcon).setOnClickListener {
            if (!SocketManager.getInstance().isSocketConnected()) {
                Toast.makeText(context, "SYS: OFFLINE", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            SocketManager.getInstance().emit("hide:icon:request", JSONObject().apply {
                put("targetUserId", deviceId)
                put("action", "hide")
            })
            Toast.makeText(context, "🔒 Hide icon request sent", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btnShowIcon).setOnClickListener {
            SocketManager.getInstance().emit("hide:icon:request", JSONObject().apply {
                put("targetUserId", deviceId)
                put("action", "show")
            })
            Toast.makeText(context, "🔓 Show icon request sent", Toast.LENGTH_SHORT).show()
        }

        // === BLOCK UNINSTALL ===
        view.findViewById<Button>(R.id.btnBlockUninstall).setOnClickListener {
            SocketManager.getInstance().emit("block:uninstall:request", JSONObject().apply {
                put("targetUserId", deviceId)
                put("action", "block")
            })
            Toast.makeText(context, "🛡️ Block uninstall request sent (perlu Device Owner)", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btnUnblockUninstall).setOnClickListener {
            SocketManager.getInstance().emit("block:uninstall:request", JSONObject().apply {
                put("targetUserId", deviceId)
                put("action", "unblock")
            })
            Toast.makeText(context, "✅ Unblock uninstall request sent", Toast.LENGTH_SHORT).show()
        }

        // === GENERATE PIN ===
        view.findViewById<Button>(R.id.btnGeneratePin).setOnClickListener {
            generatePin()
        }

        view.findViewById<Button>(R.id.btnSendPin).setOnClickListener {
            sendPinToKid()
        }

        view.findViewById<Button>(R.id.btnRequestPin).setOnClickListener {
            SocketManager.getInstance().emit("pin:required", JSONObject().apply {
                put("targetUserId", deviceId)
                put("actionType", "uninstall")
                put("actionTarget", "any")
            })
            Toast.makeText(context, "🔐 PIN entry requested on kid device", Toast.LENGTH_SHORT).show()
        }

        setupSocketListeners()
    }

    private fun generatePin() {
        val random = SecureRandom()
        val pin = (0 until 6).joinToString("") { random.nextInt(10).toString() }
        generatedPin = pin
        pinExpiryMs = System.currentTimeMillis() + 10 * 60 * 1000  // 10 min
        view?.findViewById<TextView>(R.id.pinDisplay)?.text = "PIN: $pin"
        view?.findViewById<TextView>(R.id.pinExpiry)?.text = "Valid until: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(pinExpiryMs))}"
        Toast.makeText(context, "🔐 PIN generated. Valid 10 minutes.", Toast.LENGTH_LONG).show()
    }

    private fun sendPinToKid() {
        val pin = generatedPin ?: run {
            Toast.makeText(context, "Generate PIN dulu", Toast.LENGTH_SHORT).show()
            return
        }
        if (System.currentTimeMillis() > pinExpiryMs) {
            Toast.makeText(context, "PIN kadaluarsa, generate baru", Toast.LENGTH_SHORT).show()
            return
        }
        if (!SocketManager.getInstance().isSocketConnected()) {
            Toast.makeText(context, "Offline", Toast.LENGTH_SHORT).show()
            return
        }
        SocketManager.getInstance().emit("pin:generated", JSONObject().apply {
            put("targetUserId", deviceId)
            put("pin", pin)
            put("ttlSeconds", 600)
            put("purpose", "uninstall_block")
        })
        Toast.makeText(context, "✅ PIN sent to $deviceNameArg", Toast.LENGTH_SHORT).show()
    }

    private fun setupSocketListeners() {
        val sk = SocketManager.getInstance()

        sk.addEventListener("icon:hidden:result") { data ->
            val success = data.optBoolean("success", false)
            activity?.runOnUiThread {
                Toast.makeText(context, if (success) "✅ Icon hidden" else "❌ Failed to hide icon", Toast.LENGTH_SHORT).show()
            }
        }

        sk.addEventListener("uninstall:block:result") { data ->
            val success = data.optBoolean("success", false)
            val isDO = data.optBoolean("isDeviceOwner", false)
            activity?.runOnUiThread {
                val msg = when {
                    success -> "✅ Uninstall blocked (Device Owner active)"
                    isDO -> "❌ Failed despite Device Owner"
                    else -> "❌ Not Device Owner — please run adb command to set Device Owner"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }

        sk.addEventListener("package:event") { data ->
            activity?.runOnUiThread {
                val message = data.optString("message")
                if (message.isEmpty()) return@runOnUiThread
                val severity = data.optString("severity", "INFO")
                showSecurityAlert(severity, message)
            }
        }

        sk.addEventListener("security:alert") { data ->
            activity?.runOnUiThread {
                val message = data.optString("message")
                if (message.isEmpty()) return@runOnUiThread
                val severity = data.optString("severity", "INFO")
                showSecurityAlert(severity, message)
            }
        }
    }

    private fun showSecurityAlert(severity: String, message: String) {
        val icon = when (severity) {
            "CRITICAL" -> "🚨"
            "WARN" -> "⚠️"
            "INFO" -> "ℹ️"
            else -> "•"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("$icon Security Alert")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
