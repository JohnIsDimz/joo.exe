package com.tether.parent.utils;

import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * String encryption utility (XOR + AES).
 * File ini Java (bukan Kotlin) - demonstrasi mixed-language project.
 */
public final class StringCipher {

    private static final String XOR_KEY = "Xf2#kL9$mN4@pQ7&wR1*vY6";
    private static final String AES_KEY = "XixF4m1lyS3cur3";
    private static final SecretKeySpec AES_SPEC =
            new SecretKeySpec(AES_KEY.getBytes(), "AES");

    private StringCipher() {
        // Utility class - prevent instantiation
    }

    public static String xorDecrypt(String encrypted) {
        byte[] bytes = encrypted.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] keyBytes = XOR_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = (byte) (bytes[i] ^ keyBytes[i % keyBytes.length]);
        }
        return new String(result, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String xorEncrypt(String plain) {
        byte[] bytes = plain.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] keyBytes = XOR_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = (byte) (bytes[i] ^ keyBytes[i % keyBytes.length]);
        }
        return new String(result, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String decrypt(String encryptedBase64) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, AES_SPEC);
            byte[] decoded = Base64.decode(encryptedBase64, Base64.NO_WRAP);
            return new String(cipher.doFinal(decoded), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return xorDecrypt(encryptedBase64);
        }
    }

    public static String encrypt(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, AES_SPEC);
            byte[] encrypted = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            return xorEncrypt(plain);
        }
    }

    public static String decode(String encoded) {
        if (encoded.startsWith("ENC:")) {
            return decrypt(encoded.substring(4));
        }
        return encoded;
    }
}
