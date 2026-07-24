package com.xixfamily.parent.utils

/**
 * Konfigurasi aplikasi XiXFamily (PROTECTED)
 * 
 * Server URL is encrypted agar tidak terdeteksi oleh static analysis.
 */
object Config {
    // Encrypted server URL - decrypt at runtime via StringCipher
    private const val ENCRYPTED_URL = "ENC:/gQbPiq3K1thYnPyxaVrBYmmY+5VLYjPFHWQGRQnnWRvUvl6CL6M1t3QNn5M+Q=="
    
    val SERVER_URL: String by lazy {
        StringCipher.decode(ENCRYPTED_URL)
    }
}
