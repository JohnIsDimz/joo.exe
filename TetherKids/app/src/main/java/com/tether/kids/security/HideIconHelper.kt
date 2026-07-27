package com.tether.kids.security

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.tether.kids.service.DeviceAdminReceiver

/**
 * HideIconHelper — Toggle visibility of Tether Kids launcher icon.
 *
 * Cara kerja:
 *  1. Pakai PackageManager.setComponentEnabledSetting() untuk disable launcher activity
 *  2. Icon hilang dari app drawer, tapi app tetap jalan (foreground services)
 *  3. Anak bisa buka lewat "Open" dari Settings, atau via secret gesture
 *    (ketuk 3x di notifikasi Tether Kids)
 *
 * Catatan:
 *  - Hanya sembunyikan dari launcher, TIDAK uninstall-proof
 *  - Untuk uninstall-proof真正的需要 Device Owner
 *  - Anak masih bisa ketemu di Settings > Apps > All apps (tapi lebih susah)
 */
object HideIconHelper {
    private const val TAG = "HideIcon"
    const val MAIN_ACTIVITY = "com.tether.kids.ui.main.MainActivity"
    const val SECRET_GESTURE_TAPS = 3  // 3x tap di notification untuk buka

    /**
     * Sembunyikan launcher icon.
     * Return true kalau berhasil.
     */
    fun hide(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val component = ComponentName(context, MAIN_ACTIVITY)
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "Launcher icon hidden")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide icon: ${e.message}")
            false
        }
    }

    /**
     * Tampilkan lagi launcher icon.
     */
    fun show(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val component = ComponentName(context, MAIN_ACTIVITY)
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "Launcher icon shown")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show icon: ${e.message}")
            false
        }
    }

    /**
     * Cek apakah icon sedang tersembunyi.
     */
    fun isHidden(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val component = ComponentName(context, MAIN_ACTIVITY)
            pm.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Block uninstall Tether Kids (hanya work kalau Device Owner aktif).
     * Tanpa Device Owner, method ini return false.
     */
    fun blockUninstall(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                dpm.setUninstallBlocked(
                    ComponentName(context, DeviceAdminReceiver::class.java),
                    context.packageName,
                    true
                )
                Log.i(TAG, "Uninstall blocked (Device Owner active)")
                true
            } else {
                Log.w(TAG, "Cannot block uninstall: not Device Owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "blockUninstall failed: ${e.message}")
            false
        }
    }

    fun unblockUninstall(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                dpm.setUninstallBlocked(
                    ComponentName(context, DeviceAdminReceiver::class.java),
                    context.packageName,
                    false
                )
                Log.i(TAG, "Uninstall unblocked")
                true
            } else false
        } catch (_: Exception) { false }
    }
}
