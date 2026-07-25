package com.xixfamily.parent.ui.monitoring

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.xixfamily.parent.R
import com.xixfamily.parent.data.User
import com.xixfamily.parent.network.ApiClient
import com.xixfamily.parent.network.SocketManager
import com.xixfamily.parent.ui.control.DeviceFeatureFragment
import com.xixfamily.parent.utils.DateUtils
import com.xixfamily.parent.utils.PreferenceManager
import org.json.JSONObject

class MultiChildFragment : Fragment() {

    private lateinit var kidsCardsContainer: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var familyCodeDisplay: TextView
    private lateinit var lastSyncText: TextView
    private val kids = mutableListOf<User>()
    private val onlineIds = mutableSetOf<String>()

    override fun onCreateView(inf: LayoutInflater, c: ViewGroup?, b: Bundle?): View =
        inf.inflate(R.layout.fragment_multi_child, c, false)

    override fun onViewCreated(view: View, b: Bundle?) {
        super.onViewCreated(view, b)
        kidsCardsContainer = view.findViewById(R.id.kidsCardsContainer)
        emptyState = view.findViewById(R.id.emptyState)
        familyCodeDisplay = view.findViewById(R.id.familyCodeDisplay)
        lastSyncText = view.findViewById(R.id.lastSyncText)

        try {
            familyCodeDisplay.text = "FAMILY CODE: " + PreferenceManager.getInstance(requireContext()).getFamilyCode()
        } catch (_: Exception) {}

        listenSocket()
        loadKids()
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
                val idx = kids.indexOfFirst { it.id == userId }
                if (idx >= 0) {
                    val card = kidsCardsContainer.getChildAt(idx)
                    card?.findViewById<TextView>(R.id.kidLocation)?.text =
                        "Loc: %.4f, %.4f".format(d.optDouble("latitude", 0.0), d.optDouble("longitude", 0.0))
                    card?.findViewById<TextView>(R.id.kidBattery)?.text =
                        "Bat: %d%%".format(d.optInt("batteryLevel", 0))
                }
            }}
        }
    }

    private fun refreshStatus(userId: String, online: Boolean) {
        val idx = kids.indexOfFirst { it.id == userId }; if (idx < 0) return
        val card = kidsCardsContainer.getChildAt(idx) ?: return
        val st = card.findViewById<TextView>(R.id.kidStatus)
        st.text = if (online) "ONLINE" else "OFFLINE"
        st.setBackgroundColor(Color.parseColor(if (online) "#00FFF5" else "#404060"))
        st.setTextColor(Color.parseColor(if (online) "#0D0D2B" else "#FFFFFF"))
    }

    private fun loadKids() {
        Thread {
            try {
                val resp = ApiClient.getFamilyMembers()
                activity?.runOnUiThread {
                    kids.clear(); kidsCardsContainer.removeAllViews()
                    if (resp != null && resp.has("members")) {
                        val arr = resp.getJSONArray("members")
                        for (i in 0 until arr.length()) {
                            val u = User.fromJson(arr.getJSONObject(i))
                            if (u.role == "kid") { kids.add(u); addCard(u) }
                        }
                    }
                    emptyState.visibility = if (kids.isEmpty()) View.VISIBLE else View.GONE
                    kidsCardsContainer.visibility = if (kids.isEmpty()) View.GONE else View.VISIBLE
                }
            } catch (_: Exception) { activity?.runOnUiThread { lastSyncText.text = "STATUS: SERVER UNREACHABLE" } }
        }.start()
    }

    private fun addCard(user: User) {
        val c = LayoutInflater.from(context).inflate(R.layout.item_multi_child_card, kidsCardsContainer, false)
        c.findViewById<TextView>(R.id.kidName).text = user.name
        c.findViewById<TextView>(R.id.kidLocation).text = "Loc: ---"
        c.findViewById<TextView>(R.id.kidBattery).text = "Bat: --"
        c.findViewById<TextView>(R.id.kidCheckIn).text = "Check: --"

        val st = c.findViewById<TextView>(R.id.kidStatus)
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
        kidsCardsContainer.addView(c)
    }
}
