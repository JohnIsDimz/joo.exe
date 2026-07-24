package com.xixfamily.kids.service

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Perlindungan uninstall aktif! ✅", Toast.LENGTH_LONG).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "⚠ Perlindungan uninstall dimatikan!", Toast.LENGTH_LONG).show()
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        Toast.makeText(context, "Mode proteksi terkunci", Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.packageName, DeviceAdminReceiver::class.java.name)
        }

        const val ADMIN_POLICY_KEY = "kidsfamily_device_admin"
    }
}
