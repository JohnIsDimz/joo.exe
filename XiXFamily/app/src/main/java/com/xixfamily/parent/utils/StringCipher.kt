package com.xixfamily.parent.utils

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object StringCipher {
    
    private val XOR_KEY = "Xf2#kL9\u0024mN4@pQ7&wR1*vY6"
    private const val AES_KEY = "XixF4m1lyS3cur3"
    private val aesSpec = SecretKeySpec(AES_KEY.toByteArray(), "AES")
    
    fun xorDecrypt(encrypted: String): String {
        val bytes = encrypted.toByteArray(Charsets.UTF_8)
        val keyBytes = XOR_KEY.toByteArray(Charsets.UTF_8)
        val result = ByteArray(bytes.size)
        for (i in bytes.indices) {
            result[i] = (bytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        return String(result, Charsets.UTF_8)
    }
    
    fun xorEncrypt(plain: String): String {
        val bytes = plain.toByteArray(Charsets.UTF_8)
        val keyBytes = XOR_KEY.toByteArray(Charsets.UTF_8)
        val result = ByteArray(bytes.size)
        for (i in bytes.indices) {
            result[i] = (bytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        return String(result, Charsets.UTF_8)
    }
    
    fun decrypt(encryptedBase64: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, aesSpec)
            val decoded = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            String(cipher.doFinal(decoded), Charsets.UTF_8)
        } catch (e: Exception) {
            xorDecrypt(encryptedBase64)
        }
    }
    
    fun encrypt(plain: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, aesSpec)
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            xorEncrypt(plain)
        }
    }
    
    fun decode(encoded: String): String {
        return if (encoded.startsWith("ENC:")) {
            decrypt(encoded.substring(4))
        } else {
            encoded
        }
    }
}
