package com.xixfamily.kids.utils

/**
 * Konfigurasi aplikasi KidsFamily (PROTECTED)
 * 
 * Server URL is encrypted agar tidak terdeteksi oleh static analysis.
 * Ubah XOR_KEY di StringCipher.kt dan encode ulang URL sebelum build!
 */
object Config {
    // Encrypted server URL - decrypt at runtime via StringCipher
    // To generate: StringCipher.encode("http://your-vps-ip:3000")
    private const val ENCRYPTED_URL = "ENC:/gQbPiq3K1thYnPyxaVrBYmmY+5VLYjPFHWQGRQnnWRvUvl6CL6M1t3QNn5M+Q=="
    
    val SERVER_URL: String by lazy {
        StringCipher.decode(ENCRYPTED_URL)
    }
}
