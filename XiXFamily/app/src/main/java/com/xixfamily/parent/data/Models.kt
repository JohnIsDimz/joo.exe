package com.xixfamily.parent.data

import org.json.JSONObject

data class User(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "",
    val familyCode: String = "",
    val deviceId: String = "",
    val lastActive: String = "",
    val isOnline: Boolean = false
) {
    companion object {
        fun fromJson(json: JSONObject): User {
            return User(
                id = json.optString("id", ""),
                email = json.optString("email", ""),
                name = json.optString("name", ""),
                role = json.optString("role", ""),
                familyCode = json.optString("family_code", ""),
                deviceId = json.optString("device_id", ""),
                lastActive = json.optString("last_active", ""),
                isOnline = json.optBoolean("isOnline", false)
            )
        }
    }
}

data class LocationData(
    val userId: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val batteryLevel: Float = 0f,
    val timestamp: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): LocationData {
            return LocationData(
                userId = json.optString("userId", ""),
                name = json.optString("name", ""),
                latitude = json.optDouble("latitude", 0.0),
                longitude = json.optDouble("longitude", 0.0),
                accuracy = json.optDouble("accuracy", 0.0).toFloat(),
                batteryLevel = json.optDouble("batteryLevel", 0.0).toFloat(),
                timestamp = json.optString("timestamp", "")
            )
        }
    }
}

data class SOSAlert(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val message: String = "",
    val status: String = "active",
    val timestamp: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): SOSAlert {
            return SOSAlert(
                id = json.optString("id", ""),
                userId = json.optString("userId", ""),
                userName = json.optString("user_name", json.optString("name", "")),
                latitude = if (json.has("latitude")) json.optDouble("latitude") else null,
                longitude = if (json.has("longitude")) json.optDouble("longitude") else null,
                message = json.optString("message", "SOS Emergency!"),
                status = json.optString("status", "active"),
                timestamp = json.optString("timestamp", json.optString("created_at", ""))
            )
        }
    }
}

data class AppUsageData(
    val userId: String = "",
    val name: String = "",
    val appName: String = "",
    val packageName: String = "",
    val usageDuration: Long = 0,
    val category: String = "",
    val timestamp: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): AppUsageData {
            return AppUsageData(
                userId = json.optString("userId", ""),
                name = json.optString("name", ""),
                appName = json.optString("app_name", json.optString("appName", "")),
                packageName = json.optString("package_name", json.optString("packageName", "")),
                usageDuration = json.optLong("usage_duration", json.optLong("usageDuration", 0)),
                category = json.optString("category", "unknown"),
                timestamp = json.optString("timestamp", "")
            )
        }
    }
}

data class CheckIn(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val status: String = "ok",
    val message: String = "",
    val timestamp: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): CheckIn {
            return CheckIn(
                id = json.optString("id", ""),
                userId = json.optString("user_id", json.optString("userId", "")),
                userName = json.optString("user_name", json.optString("name", "")),
                status = json.optString("status", "ok"),
                message = json.optString("message", ""),
                timestamp = json.optString("timestamp", json.optString("created_at", ""))
            )
        }
    }
}

data class Geofence(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Int = 100
) {
    companion object {
        fun fromJson(json: JSONObject): Geofence {
            return Geofence(
                id = json.optString("id", ""),
                name = json.optString("name", ""),
                latitude = json.optDouble("latitude", 0.0),
                longitude = json.optDouble("longitude", 0.0),
                radius = json.optInt("radius", 100)
            )
        }
    }
}

data class ScreenTimeLimit(
    val id: String = "",
    val kidId: String = "",
    val kidName: String = "",
    val dailyLimitMinutes: Int = 120
) {
    companion object {
        fun fromJson(json: JSONObject): ScreenTimeLimit {
            return ScreenTimeLimit(
                id = json.optString("id", ""),
                kidId = json.optString("kid_id", json.optString("kidId", "")),
                kidName = json.optString("kid_name", json.optString("kidName", "")),
                dailyLimitMinutes = json.optInt("daily_limit_minutes", json.optInt("dailyLimitMinutes", 120))
            )
        }
    }
}

data class Notification(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val isRead: Boolean = false,
    val timestamp: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): Notification {
            return Notification(
                id = json.optString("id", ""),
                type = json.optString("type", ""),
                title = json.optString("title", ""),
                body = json.optString("body", ""),
                isRead = json.optInt("read", 0) == 1,
                timestamp = json.optString("timestamp", json.optString("created_at", ""))
            )
        }
    }
}

data class AuthResponse(
    val token: String,
    val user: User
)
