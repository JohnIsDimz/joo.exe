package com.xixfamily.kids.utils

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    companion object {
        private const val PREF_NAME = "kidsfamily_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_FAMILY_CODE = "family_code"
        private const val KEY_LOCATION_SHARING = "location_sharing"

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
    fun saveFamilyCode(code: String) { prefs.edit().putString(KEY_FAMILY_CODE, code).apply() }
    fun getFamilyCode(): String = prefs.getString(KEY_FAMILY_CODE, "") ?: ""

    fun setLocationSharing(enabled: Boolean) { prefs.edit().putBoolean(KEY_LOCATION_SHARING, enabled).apply() }
    fun isLocationSharing(): Boolean = prefs.getBoolean(KEY_LOCATION_SHARING, false)

    fun saveAuthData(json: org.json.JSONObject) {
        saveToken(json.optString("token", ""))
        val user = json.optJSONObject("user") ?: json
        saveUserId(user.optString("id", ""))
        saveUserName(user.optString("name", ""))
        saveFamilyCode(user.optString("family_code", ""))
    }

    fun clearAll() { prefs.edit().clear().apply() }
    fun isLoggedIn(): Boolean = hasToken() && getUserId().isNotEmpty()
}
