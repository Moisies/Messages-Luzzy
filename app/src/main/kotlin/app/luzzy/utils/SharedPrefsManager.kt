package app.luzzy.utils

import android.content.Context
import android.content.SharedPreferences

object SharedPrefsManager {

    private const val PREFS_NAME = "fcm_prefs"
    private const val KEY_TOKEN_REGISTERED = "token_registered"
    private const val KEY_CURRENT_TOKEN = "current_token"
    private const val KEY_REGISTRATION_DATE = "registration_date"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_PHONE_NUMBER = "phone_number"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isTokenRegistered(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TOKEN_REGISTERED, false)
    }

    fun setTokenRegistered(context: Context, registered: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_TOKEN_REGISTERED, registered)
            .putLong(KEY_REGISTRATION_DATE, System.currentTimeMillis())
            .apply()
    }

    fun getCurrentToken(context: Context): String? {
        return getPrefs(context).getString(KEY_CURRENT_TOKEN, null)
    }

    fun saveCurrentToken(context: Context, token: String) {
        getPrefs(context).edit()
            .putString(KEY_CURRENT_TOKEN, token)
            .apply()
    }

    fun getRegistrationDate(context: Context): Long {
        return getPrefs(context).getLong(KEY_REGISTRATION_DATE, 0L)
    }

    fun clearTokenData(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_TOKEN_REGISTERED)
            .remove(KEY_CURRENT_TOKEN)
            .remove(KEY_REGISTRATION_DATE)
            .apply()
    }

    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    fun saveAuthToken(context: Context, token: String) {
        getPrefs(context).edit()
            .putString(KEY_AUTH_TOKEN, token)
            .apply()
    }

    fun getAuthToken(context: Context): String? {
        return getPrefs(context).getString(KEY_AUTH_TOKEN, null)
    }

    fun getAuthHeader(context: Context): String? {
        val token = getAuthToken(context)
        return if (token != null) "Bearer $token" else null
    }

    fun savePhoneNumber(context: Context, phone: String) {
        getPrefs(context).edit()
            .putString(KEY_PHONE_NUMBER, phone)
            .apply()
    }

    fun getPhoneNumber(context: Context): String? {
        return getPrefs(context).getString(KEY_PHONE_NUMBER, null)
    }
}
