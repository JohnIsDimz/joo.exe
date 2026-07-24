package com.xixfamily.parent.utils

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class PreferenceManager(context: Context) {
    companion object {
        private const val PREF_NAME = "xixfamily_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_JSON = "user_json"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_FAMILY_CODE = "family_code"

        @Volatile
        private var instance: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return instance ?: synchronized(this) {
                instance ?: PreferenceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveToken(token: String) { prefs.edit().putString(KEY_TOKEN, token).apply() }
    fun getToken(): String = prefs.getString(KEY_TOKEN, "") ?: ""
    fun hasToken(): Boolean = getToken().isNotEmpty()

    fun saveUserId(id: String) { prefs.edit().putString(KEY_USER_ID, id).apply() }
    fun getUserId(): String = prefs.getString(KEY_USER_ID, "") ?: ""
    fun saveUserName(name: String) { prefs.edit().putString(KEY_USER_NAME, name).apply() }
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""
    fun saveUserRole(role: String) { prefs.edit().putString(KEY_USER_ROLE, role).apply() }
    fun getUserRole(): String = prefs.getString(KEY_USER_ROLE, "") ?: ""
    fun saveFamilyCode(code: String) { prefs.edit().putString(KEY_FAMILY_CODE, code).apply() }
    fun getFamilyCode(): String = prefs.getString(KEY_FAMILY_CODE, "") ?: ""

    fun saveUserData(json: JSONObject) {
        val user = json.optJSONObject("user") ?: json
        saveUserId(user.optString("id", ""))
        saveUserName(user.optString("name", ""))
        saveUserRole(user.optString("role", ""))
        saveFamilyCode(user.optString("family_code", ""))
        prefs.edit().putString(KEY_USER_JSON, user.toString()).apply()
    }

    fun saveAuthResponse(json: JSONObject) {
        val token = json.optString("token", "")
        saveToken(token)
        saveUserData(json)
    }

    fun clearAll() { prefs.edit().clear().apply() }
    fun isLoggedIn(): Boolean = hasToken() && getUserId().isNotEmpty()
}
