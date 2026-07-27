package com.tether.parent.data;

import org.json.JSONObject;

import java.util.Objects;

/**
 * Data model classes untuk Tether.
 * Semua class ini ditulis dalam Java (bukan Kotlin) sebagai
 * demonstrasi mixed-language project: Kotlin & Java bisa hidup bareng.
 *
 * Catatan migrasi dari Kotlin → Java:
 *   - data class → public class dengan getter/setter
 *   - default value → constructor overloading atau initialization
 *   - companion object → static method di class itu sendiri
 *   - val → final field
 *   - isOnline Boolean → boolean (lowercase) + getter "isOnline()"
 */
public class Models {

    private Models() { }

    // ============================================================
    // User
    // ============================================================
    public static class User {
        private String id = "";
        private String email = "";
        private String name = "";
        private String role = "";
        private String familyCode = "";
        private String deviceId = "";
        private String lastActive = "";
        private boolean isOnline = false;

        public User() { }

        public User(String id, String email, String name, String role,
                   String familyCode, String deviceId, String lastActive, boolean isOnline) {
            this.id = id;
            this.email = email;
            this.name = name;
            this.role = role;
            this.familyCode = familyCode;
            this.deviceId = deviceId;
            this.lastActive = lastActive;
            this.isOnline = isOnline;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getFamilyCode() { return familyCode; }
        public void setFamilyCode(String familyCode) { this.familyCode = familyCode; }

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

        public String getLastActive() { return lastActive; }
        public void setLastActive(String lastActive) { this.lastActive = lastActive; }

        public boolean isOnline() { return isOnline; }
        public void setOnline(boolean online) { isOnline = online; }

        public static User fromJson(JSONObject json) {
            return new User(
                json.optString("id", ""),
                json.optString("email", ""),
                json.optString("name", ""),
                json.optString("role", ""),
                json.optString("family_code", ""),
                json.optString("device_id", ""),
                json.optString("last_active", ""),
                json.optBoolean("isOnline", false)
            );
        }
    }

    // ============================================================
    // LocationData
    // ============================================================
    public static class LocationData {
        private String userId = "";
        private String name = "";
        private double latitude = 0.0;
        private double longitude = 0.0;
        private float accuracy = 0f;
        private float batteryLevel = 0f;
        private String timestamp = "";

        public LocationData() { }

        public static LocationData fromJson(JSONObject json) {
            LocationData data = new LocationData();
            data.userId = json.optString("userId", "");
            data.name = json.optString("name", "");
            data.latitude = json.optDouble("latitude", 0.0);
            data.longitude = json.optDouble("longitude", 0.0);
            data.accuracy = (float) json.optDouble("accuracy", 0.0);
            data.batteryLevel = (float) json.optDouble("batteryLevel", 0.0);
            data.timestamp = json.optString("timestamp", "");
            return data;
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }
        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }
        public float getAccuracy() { return accuracy; }
        public void setAccuracy(float accuracy) { this.accuracy = accuracy; }
        public float getBatteryLevel() { return batteryLevel; }
        public void setBatteryLevel(float batteryLevel) { this.batteryLevel = batteryLevel; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }

    // ============================================================
    // SOSAlert
    // ============================================================
    public static class SOSAlert {
        private String id = "";
        private String userId = "";
        private String userName = "";
        private Double latitude = null;
        private Double longitude = null;
        private String message = "";
        private String status = "active";
        private String timestamp = "";

        public SOSAlert() { }

        public static SOSAlert fromJson(JSONObject json) {
            SOSAlert alert = new SOSAlert();
            alert.id = json.optString("id", "");
            alert.userId = json.optString("userId", "");
            alert.userName = json.optString("user_name", json.optString("name", ""));
            alert.latitude = json.has("latitude") ? json.optDouble("latitude") : null;
            alert.longitude = json.has("longitude") ? json.optDouble("longitude") : null;
            alert.message = json.optString("message", "SOS Emergency!");
            alert.status = json.optString("status", "active");
            alert.timestamp = json.optString("timestamp", json.optString("created_at", ""));
            return alert;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }

    // ============================================================
    // AppUsageData
    // ============================================================
    public static class AppUsageData {
        private String userId = "";
        private String name = "";
        private String appName = "";
        private String packageName = "";
        private long usageDuration = 0;
        private String category = "";
        private String timestamp = "";

        public AppUsageData() { }

        public static AppUsageData fromJson(JSONObject json) {
            AppUsageData data = new AppUsageData();
            data.userId = json.optString("userId", "");
            data.name = json.optString("name", "");
            data.appName = json.optString("app_name", json.optString("appName", ""));
            data.packageName = json.optString("package_name", json.optString("packageName", ""));
            data.usageDuration = json.optLong("usage_duration", json.optLong("usageDuration", 0));
            data.category = json.optString("category", "unknown");
            data.timestamp = json.optString("timestamp", "");
            return data;
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }
        public long getUsageDuration() { return usageDuration; }
        public void setUsageDuration(long usageDuration) { this.usageDuration = usageDuration; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }

    // ============================================================
    // CheckIn
    // ============================================================
    public static class CheckIn {
        private String id = "";
        private String userId = "";
        private String userName = "";
        private String status = "ok";
        private String message = "";
        private String timestamp = "";

        public CheckIn() { }

        public static CheckIn fromJson(JSONObject json) {
            CheckIn ci = new CheckIn();
            ci.id = json.optString("id", "");
            ci.userId = json.optString("user_id", json.optString("userId", ""));
            ci.userName = json.optString("user_name", json.optString("name", ""));
            ci.status = json.optString("status", "ok");
            ci.message = json.optString("message", "");
            ci.timestamp = json.optString("timestamp", json.optString("created_at", ""));
            return ci;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }

    // ============================================================
    // Geofence
    // ============================================================
    public static class Geofence {
        private String id = "";
        private String name = "";
        private double latitude = 0.0;
        private double longitude = 0.0;
        private int radius = 100;

        public Geofence() { }

        public static Geofence fromJson(JSONObject json) {
            Geofence g = new Geofence();
            g.id = json.optString("id", "");
            g.name = json.optString("name", "");
            g.latitude = json.optDouble("latitude", 0.0);
            g.longitude = json.optDouble("longitude", 0.0);
            g.radius = json.optInt("radius", 100);
            return g;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }
        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }
        public int getRadius() { return radius; }
        public void setRadius(int radius) { this.radius = radius; }
    }

    // ============================================================
    // ScreenTimeLimit
    // ============================================================
    public static class ScreenTimeLimit {
        private String id = "";
        private String deviceId = "";
        private String deviceName = "";
        private int dailyLimitMinutes = 120;

        public ScreenTimeLimit() { }

        public static ScreenTimeLimit fromJson(JSONObject json) {
            ScreenTimeLimit s = new ScreenTimeLimit();
            s.id = json.optString("id", "");
            s.deviceId = json.optString("device_id", json.optString("deviceId", ""));
            s.deviceName = json.optString("device_name", json.optString("deviceName", ""));
            s.dailyLimitMinutes = json.optInt("daily_limit_minutes", json.optInt("dailyLimitMinutes", 120));
            return s;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
        public int getDailyLimitMinutes() { return dailyLimitMinutes; }
        public void setDailyLimitMinutes(int dailyLimitMinutes) {
            this.dailyLimitMinutes = dailyLimitMinutes;
        }
    }

    // ============================================================
    // Notification
    // ============================================================
    public static class Notification {
        private String id = "";
        private String type = "";
        private String title = "";
        private String body = "";
        private boolean isRead = false;
        private String timestamp = "";

        public Notification() { }

        public static Notification fromJson(JSONObject json) {
            Notification n = new Notification();
            n.id = json.optString("id", "");
            n.type = json.optString("type", "");
            n.title = json.optString("title", "");
            n.body = json.optString("body", "");
            n.isRead = json.optInt("read", 0) == 1;
            n.timestamp = json.optString("timestamp", json.optString("created_at", ""));
            return n;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public boolean isRead() { return isRead; }
        public void setRead(boolean read) { isRead = read; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }

    // ============================================================
    // AuthResponse
    // ============================================================
    public static class AuthResponse {
        private final String token;
        private final User user;

        public AuthResponse(String token, User user) {
            this.token = token;
            this.user = user;
        }

        public String getToken() { return token; }
        public User getUser() { return user; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AuthResponse)) return false;
            AuthResponse that = (AuthResponse) o;
            return Objects.equals(token, that.token) && Objects.equals(user, that.user);
        }

        @Override
        public int hashCode() {
            return Objects.hash(token, user);
        }
    }
}
