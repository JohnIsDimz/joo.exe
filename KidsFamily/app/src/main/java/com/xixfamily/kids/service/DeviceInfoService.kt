package com.xixfamily.kids.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.*
import android.os.storage.StorageManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.util.Log
import com.xixfamily.kids.network.SocketManager
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class DeviceInfoService : Service() {

    companion object {
        private const val TAG = "DeviceInfo"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "COLLECT" -> collectAndSendDeviceInfo()
            "STOP" -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun collectAndSendDeviceInfo() {
        Thread {
            try {
                val info = JSONObject()
                
                // ===== SYSTEM INFO =====
                info.put("os", "Android ${Build.VERSION.RELEASE}")
                info.put("sdk", Build.VERSION.SDK_INT)
                info.put("build", "${Build.DISPLAY}")
                info.put("manufacturer", Build.MANUFACTURER)
                info.put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                info.put("device", Build.DEVICE)
                info.put("product", Build.PRODUCT)
                info.put("hostname", Build.HOST)
                info.put("bootloader", Build.BOOTLOADER)
                info.put("hardware", Build.HARDWARE)
                info.put("fingerprint", Build.FINGERPRINT)
                info.put("securityPatch", Build.VERSION.SECURITY_PATCH)
                
                // Build type
                info.put("buildType", Build.TYPE)
                info.put("buildTime", Build.TIME)
                info.put("buildUser", Build.USER)
                
                // ===== DISPLAY =====
                val metrics = DisplayMetrics()
                val wm = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
                wm.defaultDisplay.getRealMetrics(metrics)
                info.put("screenWidth", metrics.widthPixels)
                info.put("screenHeight", metrics.heightPixels)
                info.put("screenDensity", "${metrics.densityDpi} dpi")
                info.put("screenDensityBucket", when {
                    metrics.densityDpi <= 120 -> "ldpi"
                    metrics.densityDpi <= 160 -> "mdpi"
                    metrics.densityDpi <= 240 -> "hdpi"
                    metrics.densityDpi <= 320 -> "xhdpi"
                    metrics.densityDpi <= 480 -> "xxhdpi"
                    metrics.densityDpi <= 640 -> "xxxhdpi"
                    else -> "xxxxhdpi"
                })
                info.put("screenRefreshRate", "${wm.defaultDisplay.refreshRate} Hz")
                
                // ===== MEMORY =====
                val memInfo = ActivityManager.MemoryInfo()
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                am.getMemoryInfo(memInfo)
                
                info.put("totalRam", formatBytes(memInfo.totalMem))
                info.put("availableRam", formatBytes(memInfo.availMem))
                info.put("ramUsagePercent", String.format("%.1f",
                    (1.0 - memInfo.availMem.toDouble() / memInfo.totalMem.toDouble()) * 100))
                info.put("lowMemory", memInfo.lowMemory)
                info.put("thresholdMemory", formatBytes(memInfo.threshold))
                
                // ===== STORAGE =====
                try {
                    val statFs = StatFs(Environment.getDataDirectory().absolutePath)
                    val blockSize = statFs.blockSizeLong
                    val totalBlocks = statFs.blockCountLong
                    val availableBlocks = statFs.availableBlocksLong
                    
                    info.put("totalStorage", formatBytes(blockSize * totalBlocks))
                    info.put("availableStorage", formatBytes(blockSize * availableBlocks))
                    info.put("usedStorage", formatBytes(blockSize * (totalBlocks - availableBlocks)))
                    info.put("storageUsagePercent", String.format("%.1f",
                        (1.0 - availableBlocks.toDouble() / totalBlocks.toDouble()) * 100))
                } catch (_: Exception) {}
                
                // ===== BATTERY =====
                val batteryIntent = registerReceiver(null, 
                    android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                if (batteryIntent != null) {
                    val level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                    val temperature = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0)
                    val voltage = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0)
                    val status = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                    val plugged = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
                    
                    info.put("batteryLevel", if (level >= 0 && scale > 0) 
                        "${(level * 100.0 / scale).toInt()}%" else "Unknown")
                    info.put("batteryTemperature", "${temperature / 10.0}°C")
                    info.put("batteryVoltage", "${voltage / 1000.0}V")
                    info.put("batteryCharging", status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || 
                        status == android.os.BatteryManager.BATTERY_STATUS_FULL)
                    info.put("batteryStatus", when (status) {
                        android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                        android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                        android.os.BatteryManager.BATTERY_STATUS_FULL -> "Full"
                        android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                        android.os.BatteryManager.BATTERY_STATUS_UNKNOWN -> "Unknown"
                        else -> "Unknown"
                    })
                    info.put("batteryPlugged", when (plugged) {
                        android.os.BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                        android.os.BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                        android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                        else -> "Unplugged"
                    })
                }
                
                // ===== CPU INFO =====
                try {
                    info.put("cpuArch", System.getProperty("os.arch") ?: "Unknown")
                    info.put("cpuCores", Runtime.getRuntime().availableProcessors())
                    
                    // Parse /proc/cpuinfo
                    val cpuInfo = readFile("/proc/cpuinfo")
                    val cpuLines = cpuInfo.split("\n")
                    for (line in cpuLines) {
                        if (line.startsWith("Processor") || line.startsWith("Hardware") || 
                            line.startsWith("model name")) {
                            val parts = line.split(":")
                            if (parts.size >= 2) {
                                info.put("cpuInfo", parts[1].trim())
                            }
                            break
                        }
                    }
                    
                    // Parse CPU frequency
                    try {
                        val maxFreq = readFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
                        if (maxFreq.isNotEmpty()) {
                            info.put("cpuMaxFreq", "${maxFreq.trim().toInt() / 1000} MHz")
                        }
                    } catch (_: Exception) {}
                    
                } catch (_: Exception) {}
                
                // ===== GPS / SENSORS =====
                val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
                val sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL)
                info.put("sensorCount", sensorList.size)
                
                val hasGPS = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
                val hasNFC = packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
                val hasBluetooth = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
                val hasFingerprint = packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
                val hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
                val hasGyroscope = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)
                val hasAccelerometer = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
                val hasCompass = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS)
                val hasProximity = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY)
                val hasLightSensor = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT)
                
                info.put("hasGPS", hasGPS)
                info.put("hasNFC", hasNFC)
                info.put("hasBluetooth", hasBluetooth)
                info.put("hasFingerprint", hasFingerprint)
                info.put("hasCamera", hasCamera)
                info.put("hasGyroscope", hasGyroscope)
                info.put("hasAccelerometer", hasAccelerometer)
                info.put("hasCompass", hasCompass)
                info.put("hasProximity", hasProximity)
                info.put("hasLightSensor", hasLightSensor)
                
                // ===== NETWORK =====
                try {
                    val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                    val network = cm.activeNetwork
                    val caps = cm.getNetworkCapabilities(network)
                    
                    info.put("networkType", when {
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile Data"
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                        else -> "Unknown"
                    })
                    
                    val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                    val wifiInfo = wifiManager.connectionInfo
                    info.put("wifiSSID", wifiInfo?.ssid?.removeSurrounding("\"") ?: "Not connected")
                    
                } catch (_: Exception) {}
                
                // ===== ANDROID IDs =====
                info.put("androidId", Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID))
                info.put("deviceSerial", Build.SERIAL)
                
                // ===== APP INFO =====
                try {
                    val pkgInfo = packageManager.getPackageInfo(packageName, 0)
                    info.put("appVersion", pkgInfo.versionName ?: "Unknown")
                    info.put("appVersionCode", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) 
                        pkgInfo.longVersionCode else pkgInfo.versionCode.toLong())
                    info.put("appInstalled", pkgInfo.firstInstallTime)
                    info.put("appUpdated", pkgInfo.lastUpdateTime)
                } catch (_: Exception) {}
                
                // ===== UPTIME / TIME =====
                info.put("deviceUptime", formatDuration(SystemClock.elapsedRealtime()))
                info.put("deviceTime", java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    java.util.Locale.US
                ).format(java.util.Date()))
                info.put("timezone", java.util.TimeZone.getDefault().id)
                
                // ===== SUMMARY =====
                info.put("isRooted", checkRooted())
                
                // Send via WebSocket
                if (SocketManager.getInstance().isSocketConnected()) {
                    SocketManager.getInstance().emit("device:info:result", JSONObject().apply {
                        put("deviceInfo", info)
                    })
                    Log.d(TAG, "Device info sent successfully")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting device info: ${e.message}")
            }
        }.start()
        
        stopSelf()
    }
    
    private fun checkRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        
        for (path in paths) {
            if (File(path).exists()) return true
        }
        
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            reader.close()
            line != null && line.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
    
    private fun readFile(path: String): String {
        return try {
            File(path).readText()
        } catch (_: Exception) { "" }
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return if (days > 0) "${days}d ${hours % 24}h ${minutes % 60}m"
        else if (hours > 0) "${hours}h ${minutes % 60}m"
        else "${minutes}m"
    }
}
