# KidsFamily ProGuard Rules - Aggressive Obfuscation

# General
-optimizationpasses 5
-overloadaggressively
-repackageclasses ''
-allowaccessmodification
-mergeinterfacesaggressively
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Keep only essentials
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes EnclosingMethod

# Socket.IO must stay
-keep class io.socket.** { *; }
-dontwarn io.socket.**
-keep class org.json.** { *; }

# Obfuscate all our classes aggressively
-flattenpackagehierarchy 'x'

# Keep only entry points
-keep public class * extends android.app.Activity { *; }
-keep public class * extends android.app.Service { *; }
-keep public class * extends android.content.BroadcastReceiver { *; }
-keep public class * extends android.app.admin.DeviceAdminReceiver { *; }

# Keep AndroidX
-dontwarn androidx.**
-keep class androidx.** { *; }

# Google Play Services
-dontwarn com.google.**
-keep class com.google.android.gms.** { *; }

# Strip Log calls
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Remove unused code
-dontpreverify
-whyareyoukeeping

# Obfuscate strings in our classes
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep reflection targets
-keepclassmembers class com.xixfamily.kids.** { *; }
