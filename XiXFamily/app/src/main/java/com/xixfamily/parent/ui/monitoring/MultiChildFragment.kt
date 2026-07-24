package com.xixfamily.parent.ui.monitoring

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.xixfamily.parent.R
import com.xixfamily.parent.data.*
import com.xixfamily.parent.network.ApiClient
import com.xixfamily.parent.network.SocketManager
import com.xixfamily.parent.ui.control.DeviceFeatureFragment
import com.xixfamily.parent.utils.DateUtils
import com.xixfamily.parent.utils.PreferenceManager
import org.json.JSONObject

class MultiChildFragment : Fragment() {

    private lateinit var prefs: PreferenceManager
    private lateinit var statsBar: LinearLayout
    private lateinit var totalKidsText: TextView
    private lateinit var onlineKidsText: TextView
    private lateinit var alertKidsText: TextView
    private lateinit var kidsCardsContainer: LinearLayout
    private lateinit var emptyState: TextView
    private lateinit var btnRefresh: Button

    private val kids = mutableListOf<User>()
    private val kidLocations = mutableMapOf<String, LocationData>()
    private val kidCheckIns = mutableMapOf<String, CheckIn>()
    private val onlineUsers = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_multi_child, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferenceManager.getInstance(requireContext())

        statsBar = view.findViewById(R.id.statsBar)
        totalKidsText = view.findViewById(R.id.totalKidsText)
        onlineKidsText = view.findViewById(R.id.onlineKidsText)
        alertKidsText = view.findViewById(R.id.alertKidsText)
        kidsCardsContainer = view.findViewById(R.id.kidsCardsContainer)
        emptyState = view.findViewById(R.id.emptyState)
        btnRefresh = view.findViewById(R.id.btnRefresh)

        btnRefresh.setOnClickListener { loadData() }

        setupSocketListeners()
        loadData()
    }

    private fun setupSocketListeners() {
        val socket = SocketManager.getInstance()

        socket.addEventListener("user:online") { data ->
            val userId = data.optString("userId", "")
            activity?.runOnUiThread {
                onlineUsers.add(userId)
                updateKidCardStatus(userId, true)
                updateStats()
            }
        }

        socket.addEventListener("user:offline") { data ->
            val userId = data.optString("userId", "")
            activity?.runOnUiThread {
                onlineUsers.remove(userId)
                updateKidCardStatus(userId, false)
                updateStats()
            }
        }

        socket.addEventListener("location:updated") { data ->
            val location = LocationData.fromJson(data)
            activity?.runOnUiThread {
                kidLocations[location.userId] = location
                updateKidCardLocation(location)
            }
        }

        socket.addEventListener("checkin:received") { data ->
            val checkin = CheckIn.fromJson(data)
            activity?.runOnUiThread {
                kidCheckIns[checkin.userId] = checkin
                updateKidCardCheckIn(checkin)
            }
        }

        socket.addEventListener("sos:alert") { data ->
            val alert = SOSAlert.fromJson(data)
            activity?.runOnUiThread {
                updateKidCardSOS(alert.userId, alert)
                updateStats()
            }
        }
    }

    private fun loadData() {
        loadKids()
    }

    private fun loadKids() {
        Thread {
            val response = ApiClient.getFamilyMembers()
            activity?.runOnUiThread {
                kids.clear()
                kidsCardsContainer.removeAllViews()

                if (response != null && response.has("members")) {
                    val membersArray = response.getJSONArray("members")
                    for (i in 0 until membersArray.length()) {
                        val user = User.fromJson(membersArray.getJSONObject(i))
                        if (user.role == "kid") {
                            kids.add(user)
                            createKidCard(user)
                        }
                    }

                    if (kids.isEmpty()) {
                        emptyState.visibility = View.VISIBLE
                        kidsCardsContainer.visibility = View.GONE
                    } else {
                        emptyState.visibility = View.GONE
                        kidsCardsContainer.visibility = View.VISIBLE
                        updateStats()
                        loadKidDetails()
                    }
                }
            }
        }.start()
    }

    private fun loadKidDetails() {
        for (kid in kids) {
            // Load latest location
            Thread {
                val locResponse = ApiClient.getLatestLocation(kid.id)
                activity?.runOnUiThread {
                    if (locResponse != null && locResponse.has("location") && !locResponse.isNull("location")) {
                        val locData = locResponse.getJSONObject("location")
                        val location = LocationData(
                            userId = kid.id,
                            latitude = locData.optDouble("latitude", 0.0),
                            longitude = locData.optDouble("longitude", 0.0),
                            accuracy = locData.optDouble("accuracy", 0.0).toFloat(),
                            batteryLevel = locData.optDouble("battery_level", 0.0).toFloat(),
                            timestamp = locData.optString("timestamp", "")
                        )
                        kidLocations[kid.id] = location
                        updateKidCardLocation(location)
                    }
                }
            }.start()

            // Load latest check-in
            Thread {
                val checkinResponse = ApiClient.getCheckIns()
                activity?.runOnUiThread {
                    if (checkinResponse != null && checkinResponse.has("checkins")) {
                        val checkinsArray = checkinResponse.getJSONArray("checkins")
                        for (i in 0 until checkinsArray.length()) {
                            val ck = CheckIn.fromJson(checkinsArray.getJSONObject(i))
                            if (ck.userId == kid.id) {
                                kidCheckIns[kid.id] = ck
                                updateKidCardCheckIn(ck)
                                break
                            }
                        }
                    }
                }
            }.start()
        }
    }

    private fun createKidCard(user: User) {
        val inflater = LayoutInflater.from(context)
        val card = inflater.inflate(R.layout.item_multi_child_card, kidsCardsContainer, false)

        // Profile section
        val avatarText = card.findViewById<TextView>(R.id.kidAvatar)
        val nameText = card.findViewById<TextView>(R.id.kidName)
        val statusBadge = card.findViewById<TextView>(R.id.kidStatus)
        val lastSeenText = card.findViewById<TextView>(R.id.kidLastSeen)

        // Info section
        val locationText = card.findViewById<TextView>(R.id.kidLocation)
        val batteryText = card.findViewById<TextView>(R.id.kidBattery)
        val checkinText = card.findViewById<TextView>(R.id.kidCheckIn)

        // Set basic info
        avatarText.text = user.name.take(1).uppercase()
        nameText.text = user.name

        if (onlineUsers.contains(user.id)) {
            statusBadge.text = "ONLINE"
            statusBadge.setBackgroundResource(R.drawable.badge_online)
        } else {
            statusBadge.text = "OFFLINE"
            statusBadge.setBackgroundResource(R.drawable.badge_offline)
        }

        lastSeenText.text = if (user.lastActive.isNotEmpty())
            "Last: ${DateUtils.getRelativeTime(user.lastActive)}"
        else "Never online"

        // Initial placeholder values
        locationText.text = "Location: Waiting..."
        batteryText.text = "Battery: --"
        checkinText.text = "Check-in: --"

        // Click card → open feature menu
        card.setOnClickListener {
            val featureFrag = DeviceFeatureFragment.newInstance(user.id, user.name, onlineUsers.contains(user.id))
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, featureFrag)
                .addToBackStack("feature")
                .commit()
        }

        kidsCardsContainer.addView(card)
    }

    private fun updateKidCardLocation(location: LocationData) {
        val index = kids.indexOfFirst { it.id == location.userId }
        if (index < 0) return

        val card = kidsCardsContainer.getChildAt(index)
        if (card == null) return

        val locationText = card.findViewById<TextView>(R.id.kidLocation)
        val batteryText = card.findViewById<TextView>(R.id.kidBattery)

        locationText.text = String.format("Loc: %.4f, %.4f", location.latitude, location.longitude)
        batteryText.text = "Battery: ${location.batteryLevel.toInt()}%"
    }

    private fun updateKidCardCheckIn(checkin: CheckIn) {
        val index = kids.indexOfFirst { it.id == checkin.userId }
        if (index < 0) return

        val card = kidsCardsContainer.getChildAt(index)
        if (card == null) return

        val checkinText = card.findViewById<TextView>(R.id.kidCheckIn)

        when (checkin.status) {
            "ok" -> checkinText.text = "Check-in: Safe"
            "safe" -> checkinText.text = "Check-in: Good"
            "help" -> {
                checkinText.text = "Check-in: Need Help!"
                checkinText.setTextColor(resources.getColor(R.color.status_danger, context?.theme))
            }
            else -> checkinText.text = "Check-in: ${checkin.status}"
        }
    }

    private fun updateKidCardStatus(userId: String, isOnline: Boolean) {
        val index = kids.indexOfFirst { it.id == userId }
        if (index < 0) return

        val card = kidsCardsContainer.getChildAt(index)
        if (card == null) return

        val statusBadge = card.findViewById<TextView>(R.id.kidStatus)
        statusBadge.text = if (isOnline) "ONLINE" else "OFFLINE"
        statusBadge.setBackgroundResource(
            if (isOnline) R.drawable.badge_online else R.drawable.badge_offline
        )
    }

    private fun updateKidCardSOS(userId: String, alert: SOSAlert) {
        val index = kids.indexOfFirst { it.id == userId }
        if (index < 0) return

        val card = kidsCardsContainer.getChildAt(index)
        if (card == null) return

        Toast.makeText(context, "SOS from ${alert.userName}!", Toast.LENGTH_LONG).show()
    }

    private fun updateStats() {
        val total = kids.size
        val online = kids.count { onlineUsers.contains(it.id) }
        totalKidsText.text = total.toString()
        onlineKidsText.text = online.toString()
        alertKidsText.text = "0"
    }
}
