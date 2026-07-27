package com.tether.kids.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BootReceiver — Auto-start ALL monitor services saat HP selesai boot.
 *
 * Sebelumnya: cuma start LocationService. Sekarang: start semua service
 * agar monitoring tidak terputus saat HP restart.
 *
 * Services yang di-start:
 *  - LocationService (perlu permission)
 *  - BatteryMonitorService (no permission)
 *  - NetworkMonitorService (no permission)
 *  - ClipboardMonitorService (no permission)
 *  - AppUsageService (perlu PACKAGE_USAGE_STATS)
 *
 * SmsMonitorService adalah NotificationListenerService — auto-start sendiri
 * setelah user enable di Settings, tidak perlu start manual dari sini.
 */
class BootReceiver : BroadcastReceiver() {
    companion object { private const val TAG = "BootReceiver" }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i(TAG, "Boot/restart detected, starting all monitor services")
            startAllServices(context)
        }
    }

    private fun startAllServices(context: Context) {
        val services = listOf(
            LocationService::class.java,
            BatteryMonitorService::class.java,
            NetworkMonitorService::class.java,
            ClipboardMonitorService::class.java,
            AppUsageService::class.java
        )
        for (clazz in services) {
            try {
                val serviceIntent = Intent(context, clazz)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "Started ${clazz.simpleName}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start ${clazz.simpleName}: ${e.message}")
            }
        }
    }
}
