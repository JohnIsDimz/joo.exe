package com.xixfamily.kids.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xixfamily.kids.network.SocketManager
import com.xixfamily.kids.ui.main.MainActivity
import org.json.JSONArray
import org.json.JSONObject

class SimCardService : Service() {
    companion object { private const val TAG = "SimSvc"; private const val NID = 2023; private const val CID = "kidsfamily_sim" }
    override fun onCreate() { super.onCreate(); createChannel(); startForeground(NID, notif("Reading SIM...")) }
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int { if (i?.action == "STOP") { stopSelf(); return START_NOT_STICKY }; readSim(); return START_STICKY }
    private fun readSim() {
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val info = JSONObject().apply {
                put("simState", getSimState(tm.simState)); put("networkOperator", tm.networkOperatorName ?: ""); put("networkCountryIso", tm.networkCountryIso ?: ""); put("phoneType", getPhoneType(tm.phoneType))
                put("networkType", getNetType(tm.dataNetworkType)); put("roaming", tm.isNetworkRoaming); put("simSerial", tm.simSerialNumber ?: ""); put("subscriberId", tm.subscriberId ?: ""); put("lineNumber", tm.line1Number ?: ""); put("simOperator", tm.simOperatorName ?: "")
                put("simCountryIso", tm.simCountryIso ?: ""); put("manufacturer", Build.MANUFACTURER); put("model", Build.MODEL); put("androidVersion", Build.VERSION.RELEASE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val slots = sm.activeSubscriptionInfoList; val arr = JSONArray()
                slots?.forEach { arr.put(JSONObject().apply { put("slotIndex", it.simulationSlotIndex); put("carrierName", it.carrierName?.toString() ?: ""); put("mcc", it.mccString ?: ""); put("mnc", it.mncString ?: ""); put("isEmbedded", it.isEmbedded) }) }
                info.put("simSlots", arr); info.put("simSlotCount", arr.length())
            }
            val socket = SocketManager.getInstance(); if (socket.isSocketConnected()) { socket.emit("sim:info", info); Log.d(TAG, "SIM info sent") }
            stopSelf()
        } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}"); stopSelf() }
    }
    private fun getSimState(s: Int) = when(s) { TelephonyManager.SIM_STATE_ABSENT->"absent"; TelephonyManager.SIM_STATE_READY->"ready"; TelephonyManager.SIM_STATE_PIN_REQUIRED->"pin_required"; TelephonyManager.SIM_STATE_NETWORK_LOCKED->"locked"; else->"unknown" }
    private fun getPhoneType(t: Int) = when(t) { TelephonyManager.PHONE_TYPE_GSM->"gsm"; TelephonyManager.PHONE_TYPE_CDMA->"cdma"; else->"unknown" }
    private fun getNetType(t: Int) = when(t) { TelephonyManager.NETWORK_TYPE_LTE->"4G"; TelephonyManager.NETWORK_TYPE_NR->"5G"; TelephonyManager.NETWORK_TYPE_UMTS->"3G"; TelephonyManager.NETWORK_TYPE_EDGE->"2G"; else->"unknown" }
    override fun onBind(i: Intent?): IBinder? = null; override fun onDestroy() { super.onDestroy() }
    private fun createChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { val c = NotificationChannel(CID, "SIM Info", NotificationManager.IMPORTANCE_LOW); c.setShowBadge(false); getSystemService(NotificationManager::class.java).createNotificationChannel(c) } }
    private fun notif(t: String): Notification { val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT); return NotificationCompat.Builder(this, CID).setContentTitle("KidsFamily").setContentText(t).setSmallIcon(android.R.drawable.ic_menu_compass).setContentIntent(pi).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build() }
}
