package com.tether.kids.ui.access

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tether.kids.R
import com.tether.kids.security.HideIconHelper
import com.tether.kids.ui.main.MainActivity

/**
 * SecretAccessActivity — Akses ke Tether Kids saat launcher icon disembunyikan.
 *
 * Cara buka:
 *  1. Buka notifikasi Tether Kids (yang foreground service)
 *  2. Ketuk notification body 3x dalam 2 detik → buka SecretAccessActivity
 *  3. Atau dial nomor *#*#TETHER#*#* (*#*#833843#*#*) → trigger via PhoneWindow
 *  4. Atau scan QR code dari Tether Parent app (TODO)
 *
 * Setelah di activity ini, ada 2 opsi:
 *  - "Buka Tether Kids" → langsung ke MainActivity
 *  - "Show Icon" → un-hide launcher icon
 *
 * Activity ini juga exported=false agar tidak bisa di-trigger dari luar app.
 */
class SecretAccessActivity : AppCompatActivity() {

    companion object {
        const val ACTION_SECRET = "com.tether.kids.SECRET_ACCESS"
        private const val SECRET_CODE = "833843"  // TETHER
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secret_access)

        findViewById<TextView>(R.id.secretTitle).text = "🔐 Tether Kids — Hidden Access"
        findViewById<TextView>(R.id.secretInfo).text =
            "Icon disembunyikan. Activity ini hanya bisa dibuka lewat gesture (3x tap notifikasi) atau dial code *#*#TETHER#*#*."

        findViewById<Button>(R.id.btnOpenMain).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        findViewById<Button>(R.id.btnShowIcon).setOnClickListener {
            if (HideIconHelper.show(this)) {
                Toast.makeText(this, "Icon launcher dikembalikan", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Gagal kembalikan icon", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnClose).setOnClickListener { finish() }
    }
}
