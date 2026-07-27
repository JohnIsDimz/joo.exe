package com.tether.kids.security

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.tether.kids.R
import com.tether.kids.network.SocketManager
import com.tether.kids.utils.PreferenceManager
import org.json.JSONObject

/**
 * PinVerificationActivity — Full-screen PIN entry for sensitive actions.
 *
 * Triggered by:
 *  - Tether Kids intercepts uninstall attempt (PACKAGE_REMOVED)
 *  - Parent Tether app sends "pin:required" event → kid shows this activity
 *  - Manual: launch via `am start -n com.tether.kids/.security.PinVerificationActivity`
 *
 * Flow:
 *  1. Activity launches with action_type in extras
 *  2. User enters 6-digit PIN
 *  3. Submit → emit `pin:verify` to server
 *  4. Server replies `pin:verified:result` with allowed/blocked
 *  5. UI shows result + closes
 */
class PinVerificationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PinVerify"
        const val ACTION_TYPE = "action_type"     // "uninstall", "install", "settings"
        const val ACTION_TARGET = "action_target"  // packageName affected
        const val REQUEST_CODE = 1001

        fun launchForUninstall(context: android.content.Context, packageName: String) {
            val i = Intent(context, PinVerificationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(ACTION_TYPE, "uninstall")
                putExtra(ACTION_TARGET, packageName)
            }
            context.startActivity(i)
        }
    }

    private lateinit var prefs: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getInstance(this)

        val actionType = intent.getStringExtra(ACTION_TYPE) ?: "settings"
        val target = intent.getStringExtra(ACTION_TARGET) ?: ""

        showPinDialog(actionType, target)
    }

    private fun showPinDialog(actionType: String, target: String) {
        val builder = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
        builder.setTitle("🔐 Verifikasi Orang Tua")
        builder.setMessage(
            "Untuk $actionType${if (target.isNotEmpty()) " ($target)" else ""}, " +
            "masukkan PIN 6-digit yang diberikan orang tua Anda.\n\n" +
            "Minta PIN ke orang tua via WhatsApp / telepon."
        )

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN 6 digit"
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            textSize = 24f
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        builder.setView(input)

        builder.setPositiveButton("KIRIM") { _, _ ->
            val pin = input.text.toString().trim()
            if (pin.length == 6 && pin.all { it.isDigit() }) {
                submitPin(pin, actionType, target)
            } else {
                Toast.makeText(this, "PIN harus 6 digit angka", Toast.LENGTH_SHORT).show()
                showPinDialog(actionType, target)  // Retry
            }
        }
        builder.setNegativeButton("BATAL") { _, _ -> finish() }
        builder.setCancelable(false)

        runOnUiThread {
            val dialog = builder.create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.background_dark)
            dialog.show()
        }
    }

    private fun submitPin(pin: String, actionType: String, target: String) {
        val sk = SocketManager.getInstance()
        if (!sk.isSocketConnected()) {
            Toast.makeText(this, "❌ Tidak ada koneksi server. Coba lagi nanti.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Setup listener untuk reply
        sk.addEventListener("pin:verified:result") { data ->
            runOnUiThread {
                handleVerificationResult(data, actionType, target)
            }
            // Cleanup listener (one-shot)
            SocketManager.getInstance().removeEventListener("pin:verified:result") { /* not really removable, but will be GC'd */ }
        }

        // Kirim PIN ke server untuk verifikasi
        sk.emit("pin:verify", JSONObject().apply {
            put("pin", pin)
            put("actionType", actionType)
            put("actionTarget", target)
            put("userId", prefs.getUserId())
            put("familyCode", prefs.getFamilyCode())
            put("timestamp", System.currentTimeMillis())
        })

        Toast.makeText(this, "Mengirim PIN ke server...", Toast.LENGTH_SHORT).show()
    }

    private fun handleVerificationResult(data: JSONObject?, actionType: String, target: String) {
        if (data == null) {
            Toast.makeText(this, "❌ Server tidak merespons", Toast.LENGTH_LONG).show()
            return
        }
        val allowed = data.optBoolean("allowed", false)
        val message = data.optString("message", "")

        AlertDialog.Builder(this)
            .setTitle(if (allowed) "✅ DIIZINKAN" else "❌ DITOLAK")
            .setMessage(message.ifEmpty { if (allowed) "Aksi diizinkan" else "Aksi ditolak" })
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
}
