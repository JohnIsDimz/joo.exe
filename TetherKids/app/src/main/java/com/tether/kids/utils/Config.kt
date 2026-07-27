package com.tether.kids.utils

object Config {
    // ============================================================
    // SERVER URL — TANAM (hardcoded)
    // ============================================================
    // Ubah URL ini ke address server Anda, lalu rebuild APK.
    //
    // Contoh URL:
    //   - Emulator Android  →  http://10.0.2.2:3000
    //   - HP di jaringan WiFi lokal (PC server di 192.168.1.10):
    //                            http://192.168.1.10:3000
    //   - VPS production    →  http://your-vps-domain.com:3000
    //   - HTTPS production  →  https://api.yourdomain.com
    //
    // Setelah ganti URL, rebuild: ./gradlew clean build
    // ============================================================
    const val SERVER_URL = "http://192.168.1.1:3000"
}
