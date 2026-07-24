# XiXFamily ProGuard Rules - Aggressive Obfuscation
-optimizationpasses 5
-overloadaggressively
-repackageclasses ''
-allowaccessmodification
-mergeinterfacesaggressively
-flattenpackagehierarchy 'x'

-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes Signature

-keep class io.socket.** { *; }
-dontwarn io.socket.**
-keep class org.json.** { *; }

-keep public class * extends android.app.Activity { *; }
-keep public class * extends android.app.Service { *; }
-keep public class * extends android.content.BroadcastReceiver { *; }

-dontwarn androidx.**
-keep class androidx.** { *; }

-dontwarn com.google.**
-keep class com.google.android.gms.** { *; }

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
