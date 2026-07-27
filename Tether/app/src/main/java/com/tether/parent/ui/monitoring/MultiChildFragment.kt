package com.tether.parent.ui.monitoring

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.tether.parent.R
import com.tether.parent.data.Models.User
import com.tether.parent.network.ApiClient
import com.tether.parent.network.SocketManager
import com.tether.parent.ui.control.DeviceFeatureFragment
import com.tether.parent.utils.DateUtils
import com.tether.parent.utils.PreferenceManager
import org.json.JSONObject

class MultiChildFragment : Fragment() {

    private lateinit var deviceCardsContainer: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var familyCodeDisplay: TextView
    private lateinit var lastSyncText: TextView
    private val devices = mutableListOf<User>()
    private val onlineIds = mutableSetOf<String>()

    override fun onCreateView(inf: LayoutInflater, c: ViewGroup?, b: Bundle?): View =
        inf.inflate(R.layout.fragment_multi_child, c, false)

    override fun onViewCreated(view: View, b: Bundle?) {
        super.onViewCreated(view, b)
        deviceCardsContainer = view.findViewById(R.id.deviceCardsContainer)
        emptyState = view.findViewById(R.id.emptyState)
        familyCodeDisplay = view.findViewById(R.id.familyCodeDisplay)
        lastSyncText = view.findViewById(R.id.lastSyncText)

        try {
            familyCodeDisplay.text = "FAMILY CODE: " + PreferenceManager.getInstance(requireContext()).getFamilyCode()
        } catch (_: Exception) {}

        listenSocket()
        loadDevices()
    }

    private fun listenSocket() {
        val sk = SocketManager.getInstance()
        sk.addEventListener("socket:connected") { activity?.runOnUiThread { lastSyncText.text = "STATUS: ONLINE" } }
        sk.addEventListener("socket:disconnected") { activity?.runOnUiThread { lastSyncText.text = "STATUS: OFFLINE" } }
        sk.addEventListener("user:online") { d ->
            val id = d.optString("userId", "")
            activity?.runOnUiThread { run {
                onlineIds.add(id); refreshStatus(id, true)
            }}
        }
        sk.addEventListener("user:offline") { d ->
            val id = d.optString("userId", "")
            activity?.runOnUiThread { run {
                onlineIds.remove(id); refreshStatus(id, false)
            }}
        }
        sk.addEventListener("location:updated") { d ->
            activity?.runOnUiThread { run {
                val userId = d.optString("userId", "")
                val idx = devices.indexOfFirst { it.id == userId }
                if (idx >= 0) {
                    val card = deviceCardsContainer.getChildAt(idx)
                    card?.findViewById<TextView>(R.id.deviceLocation)?.text =
                        "Loc: %.4f, %.4f".format(d.optDouble("latitude", 0.0), d.optDouble("longitude", 0.0))
                    card?.findViewById<TextView>(R.id.deviceBattery)?.text =
                        "Bat: %d%%".format(d.optInt("batteryLevel", 0))
                }
            }}
        }
    }

    private fun refreshStatus(userId: String, online: Boolean) {
        val idx = devices.indexOfFirst { it.id == userId }; if (idx < 0) return
        val card = deviceCardsContainer.getChildAt(idx) ?: return
        val st = card.findViewById<TextView>(R.id.deviceStatus)
        st.text = if (online) "ONLINE" else "OFFLINE"
        st.setBackgroundColor(Color.parseColor(if (online) "#00FFF5" else "#404060"))
        st.setTextColor(Color.parseColor(if (online) "#0D0D2B" else "#FFFFFF"))
    }

    private fun loadDevices() {
        Thread {
            try {
                val resp = ApiClient.getFamilyMembers()
                activity?.runOnUiThread {
                    devices.clear(); deviceCardsContainer.removeAllViews()
                    if (resp != null && resp.has("members")) {
                        val arr = resp.getJSONArray("members")
                        for (i in 0 until arr.length()) {
                            val u = User.fromJson(arr.getJSONObject(i))
                            if (u.role == "kid") { devices.add(u); addCard(u) }
                        }
                    }
                    emptyState.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
                    deviceCardsContainer.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
                }
            } catch (_: Exception) { activity?.runOnUiThread { lastSyncText.text = "STATUS: SERVER UNREACHABLE" } }
        }.start()
    }

    private fun addCard(user: User) {
        val c = LayoutInflater.from(context).inflate(R.layout.item_multi_child_card, deviceCardsContainer, false)
        c.findViewById<TextView>(R.id.deviceName).text = user.name
        c.findViewById<TextView>(R.id.deviceLocation).text = "Loc: ---"
        c.findViewById<TextView>(R.id.deviceBattery).text = "Bat: --"
        c.findViewById<TextView>(R.id.deviceCheckIn).text = "Check: --"

        val st = c.findViewById<TextView>(R.id.deviceStatus)
        val on = onlineIds.contains(user.id)
        st.text = if (on) "ONLINE" else "OFFLINE"
        st.setBackgroundColor(Color.parseColor(if (on) "#00FFF5" else "#404060"))
        st.setTextColor(Color.parseColor(if (on) "#0D0D2B" else "#FFFFFF"))

        c.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, DeviceFeatureFragment.newInstance(user.id, user.name, on))
                .addToBackStack("feature")
                .commit()
        }

        // Long-press → buka DeviceSecurityFragment (anti-uninstall control)
        c.setOnLongClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, com.tether.parent.ui.security.DeviceSecurityFragment.newInstance(user.id, user.name))
                .addToBackStack("security")
                .commit()
            true
        }
        deviceCardsContainer.addView(c)
    }
}
