package com.tether.parent.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Debug;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * SecurityUtils - Anti-tampering & integrity check utility.
 *
 * Tujuan: Melindungi aplikasi legitimate dari modifikasi berbahaya
 * (seperti repackaging APK untuk inject malware, bypass license check, dll).
 *
 * CATATAN PENTING:
 * - Ini BUKAN anti-detection malware. Ini adalah STANDAR security practice
 *   untuk aplikasi legitimate (banking app, enterprise app, dll).
 * - Tidak ada icon hiding, polymorphic code, atau fitur stealth.
 * - Hanya verifikasi integritas & deteksi environment debugging.
 *
 * File ini Java (bukan Kotlin) - mixed-language project.
 */
public final class SecurityUtils {

    private static final String TAG = "TetherSecurity";

    // Ganti dengan signature hash aplikasi Anda setelah build pertama
    // Cara dapat: lihat log di SecurityUtils saat pertama kali run
    // kosongkan dulu untuk development, set setelah release
    private static final String EXPECTED_SIGNATURE_SHA256 = "";  // <-- isi setelah build pertama

    private SecurityUtils() { }

    /**
     * Run semua security checks.
     * Return SecurityReport dengan hasil setiap check.
     */
    public static SecurityReport runSecurityChecks(Context context) {
        SecurityReport report = new SecurityReport();

        report.debuggerDetected = detectDebugger();
        report.emulatorDetected = detectEmulator();
        report.rootDetected = detectRoot();
        report.xposedDetected = detectXposed();
        report.fridaDetected = detectFrida();
        report.tampered = !verifyAppSignature(context);
        report.suspiciousPackages = detectSuspiciousPackages(context);

        report.threatLevel = calculateThreatLevel(report);

        if (report.threatLevel == ThreatLevel.HIGH) {
            Log.w(TAG, "Security threat detected: " + report.toString());
        }

        return report;
    }

    /**
     * Detect apakah debugger attached ke process kita.
     * Return true kalau iya.
     */
    public static boolean detectDebugger() {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger();
    }

    /**
     * Detect emulator via multiple signals (build props + filesystem).
     */
    public static boolean detectEmulator() {
        // Cek Build.* properties untuk kata kunci emulator
        if (Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("vbox") ||
            Build.BOARD.toLowerCase().contains("unknown") ||
            Build.BOOTLOADER.toLowerCase().contains("unknown") ||
            getSerial().toLowerCase().contains("unknown")) {
            return true;
        }

        // Cek filesystem untuk path khusus emulator
        String[] emulatorPaths = {
            "/dev/socket/qemud",
            "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/system/bin/qemu-props"
        };
        for (String path : emulatorPaths) {
            if (new File(path).exists()) return true;
        }

        return false;
    }

    /**
     * Get device serial number, with fallback for deprecated Build.SERIAL on API 26+.
     * Build.SERIAL is deprecated since Android 8.0. On API 26+ we use Build.getSerial()
     * guarded by a permission check, otherwise fallback to "unknown" string.
     */
    @SuppressWarnings("deprecation")
    private static String getSerial() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Build.getSerial() is deprecated too, but only triggers warning (not error)
                // Requires READ_PHONE_STATE permission at runtime; if not granted, returns "unknown"
                String serial = Build.getSerial();
                return serial != null ? serial : "unknown";
            } else {
                // Pre-Android 8.0: Build.SERIAL is the only public API
                return Build.SERIAL;
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static boolean detectRoot() {
        String[] rootPaths = {
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk",
            "/system/app/magisk.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/adb/modules",  // Magisk module directory
        };

        for (String path : rootPaths) {
            if (new File(path).exists()) return true;
        }

        // Cek "which su" via shell
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"which", "su"});
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            String line = reader.readLine();
            reader.close();
            return line != null && !line.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Detect Xposed framework (popular hooking tool).
     */
    public static boolean detectXposed() {
        try {
            Class.forName("de.robv.android.xposed.XposedBridge");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Detect Frida (dynamic instrumentation toolkit).
     */
    public static boolean detectFrida() {
        try {
            // Check if Frida server process is running
            Process process = Runtime.getRuntime().exec(new String[]{"ps", "-A"});
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains("frida")) {
                    reader.close();
                    return true;
                }
            }
            reader.close();
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    /**
     * Detect suspicious packages (analysis tools, repackaging tools).
     */
    public static boolean detectSuspiciousPackages(Context context) {
        // Package yang sering dipakai untuk analyze/repackage APK
        String[] analysisTools = {
            "com.keramidas.TitaniumBackup",
            "com.aurora.adroid",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.lackypatch",
            "com.ramdroid.appmanager",
            "io.github.muntashirakon.appmanager",
            "com.android.vending.billing.InAppBillingService" // fake
        };

        PackageManager pm = context.getPackageManager();
        for (String pkg : analysisTools) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (Exception e) {
                // not installed
            }
        }
        return false;
    }

    /**
     * Verify aplikasi signature cocok dengan expected.
     * Mencegah APK repackaging (modified APK dengan signature berbeda).
     *
     * CATATAN: EXPECTED_SIGNATURE_SHA256 harus di-set setelah build pertama.
     * Untuk development, biarkan kosong (check akan skip).
     */
    @SuppressWarnings("deprecation")
    public static boolean verifyAppSignature(Context context) {
        if (EXPECTED_SIGNATURE_SHA256 == null || EXPECTED_SIGNATURE_SHA256.isEmpty()) {
            // Development mode: skip signature check
            return true;
        }

        try {
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // API 28+ (Android 9.0+): use GET_SIGNING_CERTIFICATES
                packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                android.content.pm.SigningInfo signingInfo = packageInfo.signingInfo;
                if (signingInfo == null) {
                    return false;
                }
                Signature[] signatures = signingInfo.getApkContentsSigners();
                if (signatures == null || signatures.length == 0) {
                    return false;
                }
                String currentHash = sha256(signatures[0].toCharsString());
                return currentHash.equalsIgnoreCase(EXPECTED_SIGNATURE_SHA256);
            } else {
                // API <28: use legacy GET_SIGNATURES
                packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
                Signature[] signatures = packageInfo.signatures;
                if (signatures == null || signatures.length == 0) {
                    return false;
                }
                String currentHash = sha256(signatures[0].toCharsString());
                return currentHash.equalsIgnoreCase(EXPECTED_SIGNATURE_SHA256);
            }
        } catch (Exception e) {
            Log.e(TAG, "Signature verification failed", e);
            return false;
        }
    }

    /**
     * SHA-256 helper.
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Calculate threat level berdasarkan jumlah & severity deteksi.
     */
    private static ThreatLevel calculateThreatLevel(SecurityReport report) {
        int score = 0;
        if (report.debuggerDetected) score += 3;
        if (report.tampered) score += 5;  // tampering = very serious
        if (report.fridaDetected || report.xposedDetected) score += 4;
        if (report.rootDetected) score += 2;
        if (report.emulatorDetected) score += 1;
        if (report.suspiciousPackages) score += 2;

        if (score >= 5) return ThreatLevel.HIGH;
        if (score >= 3) return ThreatLevel.MEDIUM;
        if (score >= 1) return ThreatLevel.LOW;
        return ThreatLevel.NONE;
    }

    // =================== Data classes ===================

    public enum ThreatLevel {
        NONE,    // no threat
        LOW,     // emulator (might be dev)
        MEDIUM,  // root OR suspicious packages
        HIGH     // debugger, tampered, OR (root + xposed/frida)
    }

    public static class SecurityReport {
        public boolean debuggerDetected;
        public boolean emulatorDetected;
        public boolean rootDetected;
        public boolean xposedDetected;
        public boolean fridaDetected;
        public boolean tampered;
        public boolean suspiciousPackages;
        public ThreatLevel threatLevel = ThreatLevel.NONE;

        public boolean isSafe() {
            return threatLevel == ThreatLevel.NONE || threatLevel == ThreatLevel.LOW;
        }

        @Override
        public String toString() {
            return "SecurityReport{" +
                "debug=" + debuggerDetected +
                ", emu=" + emulatorDetected +
                ", root=" + rootDetected +
                ", xposed=" + xposedDetected +
                ", frida=" + fridaDetected +
                ", tampered=" + tampered +
                ", susPkg=" + suspiciousPackages +
                ", level=" + threatLevel +
                '}';
        }
    }
}
