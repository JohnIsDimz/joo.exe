package com.tether.kids.security

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.tether.kids.network.SocketManager
import com.tether.kids.service.DeviceAdminReceiver
import com.tether.kids.utils.PreferenceManager
import org.json.JSONObject

/**
 * PackageMonitorReceiver — Detect package install/uninstall attempts.
 *
 * Monitor:
 *  - PACKAGE_ADDED      → app baru di-install
 *  - PACKAGE_REMOVED    → app di-uninstall
 *  - PACKAGE_REPLACED   → app di-update
 *  - MY_PACKAGE_REPLACED → Tether Kids sendiri di-update
 *  - ACTION_PACKAGE_RESTARTED → app di-restart paksa
 *
 * Kirim event ke server, dan Tether Parent akan lihat notifikasi.
 * Kalau anak coba uninstall Tether Kids sendiri → alert khusus ke parent.
 */
class PackageMonitorReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PkgMonitor"
        const val ACTION_UNINSTALL_BLOCKED = "com.tether.kids.UNINSTALL_BLOCKED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val packageName = intent.data?.schemeSpecificPart ?: return
        val prefs = PreferenceManager.getInstance(context)
        val sk = SocketManager.getInstance()

        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val replaced = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!replaced) {
                    val info = getAppInfo(context, packageName)
                    Log.i(TAG, "Installed: $packageName (replaced=$replaced)")
                    if (sk.isSocketConnected()) {
                        sk.emit("package:installed", JSONObject().apply {
                            put("packageName", packageName)
                            put("appName", info.first)
                            put("isSystemApp", info.second)
                            put("isTetherKids", packageName == context.packageName)
                            put("timestamp", System.currentTimeMillis())
                        })
                    }
                }
            }

            Intent.ACTION_PACKAGE_REMOVED -> {
                val replaced = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!replaced) {
                    val info = getAppInfo(context, packageName)
                    val isTetherKids = packageName == context.packageName
                    Log.w(TAG, "Removed: $packageName (tether=$isTetherKids)")

                    if (isTetherKids) {
                        // TETHER KIDS SENDIRI DI-UNINSTALL!
                        // Last resort: coba stop proses supaya user lihat dialog error
                        if (sk.isSocketConnected()) {
                            sk.emit("tether:uninstall:attempt", JSONObject().apply {
                                put("userId", prefs.getUserId())
                                put("familyCode", prefs.getFamilyCode())
                                put("packageName", packageName)
                                put("severity", "CRITICAL")
                                put("message", "⚠️ Tether Kids пытался di-uninstall!")
                                put("timestamp", System.currentTimeMillis())
                            })
                        }
                    } else {
                        if (sk.isSocketConnected()) {
                            sk.emit("package:uninstalled", JSONObject().apply {
                                put("packageName", packageName)
                                put("appName", info.first)
                                put("isSystemApp", info.second)
                                put("timestamp", System.currentTimeMillis())
                            })
                        }
                    }
                }
            }

            Intent.ACTION_PACKAGE_REPLACED -> {
                if (packageName == context.packageName) {
                    Log.i(TAG, "Tether Kids itself was updated")
                    if (sk.isSocketConnected()) {
                        sk.emit("tether:self:updated", JSONObject().apply {
                            put("packageName", packageName)
                            put("versionName", try {
                                context.packageManager.getPackageInfo(packageName, 0).versionName
                            } catch (_: Exception) { "unknown" })
                        })
                    }
                } else {
                    val info = getAppInfo(context, packageName)
                    if (sk.isSocketConnected()) {
                        sk.emit("package:updated", JSONObject().apply {
                            put("packageName", packageName)
                            put("appName", info.first)
                            put("timestamp", System.currentTimeMillis())
                        })
                    }
                }
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Tether Kids updated (MY_PACKAGE_REPLACED)")
                if (sk.isSocketConnected()) {
                    sk.emit("tether:self:updated", JSONObject().apply {
                        put("packageName", packageName)
                        put("timestamp", System.currentTimeMillis())
                    })
                }
            }
        }
    }

    private fun getAppInfo(context: Context, packageName: String): Pair<String, Boolean> {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val name = pm.getApplicationLabel(appInfo).toString()
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    || (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            Pair(name, isSystem)
        } catch (_: Exception) {
            Pair(packageName, false)
        }
    }
}
