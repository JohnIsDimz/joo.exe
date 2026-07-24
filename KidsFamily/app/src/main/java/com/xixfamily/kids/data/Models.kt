package com.xixfamily.kids.data

import org.json.JSONObject

data class User(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "kid",
    val familyCode: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): User {
            return User(
                id = json.optString("id", ""),
                email = json.optString("email", ""),
                name = json.optString("name", ""),
                role = json.optString("role", "kid"),
                familyCode = json.optString("family_code", "")
            )
        }
    }
}

data class AuthResponse(
    val token: String,
    val user: User
)
