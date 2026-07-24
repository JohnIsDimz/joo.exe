package com.xixfamily.kids.utils

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * StringCipher - Encrypts sensitive strings at rest, decrypts at runtime.
 * This prevents static analysis from reading hardcoded URLs, commands, and keywords.
 */
object StringCipher {
    
    // XOR key - changes with each build (rotate this)
    private const val XOR_KEY = "Xf2#kL9$mN4@pQ7&wR1*vY6"
    
    // AES key for stronger encryption
    private const val AES_KEY = "XixF4m1lyS3cur3"
    private val aesSpec = SecretKeySpec(AES_KEY.toByteArray(), "AES")
    
    /**
     * Simple XOR obfuscation - lightweight, fast
     */
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
    
    /**
     * AES decryption for more sensitive strings
     */
    fun decrypt(encryptedBase64: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, aesSpec)
            val decoded = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            String(cipher.doFinal(decoded), Charsets.UTF_8)
        } catch (e: Exception) {
            // Fallback to XOR if AES fails
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
    
    /**
     * Obfuscated string builder - splits strings into parts to avoid pattern matching
     */
    fun buildString(vararg parts: String): String {
        val sb = StringBuilder()
        for (part in parts) {
            sb.append(xorDecrypt(part))
        }
        return sb.toString()
    }
    
    /**
     * Encode a string for storage in code (obfuscated format)
     * Usage: StringCipher.encode("https://my-server.com")
     * Result: Store this in Config.kt
     */
    fun encode(plain: String): String {
        return "ENC:" + encrypt(plain)
    }
    
    /**
     * Decode a previously encoded string
     */
    fun decode(encoded: String): String {
        return if (encoded.startsWith("ENC:")) {
            decrypt(encoded.substring(4))
        } else {
            encoded // Not encrypted
        }
    }
}
