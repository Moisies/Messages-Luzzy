package com.goodwy.smsmessenger.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manager para gestionar SharedPreferences relacionadas con FCM
 */
object SharedPrefsManager {

    private const val PREFS_NAME = "fcm_prefs"
    private const val KEY_TOKEN_REGISTERED = "token_registered"
    private const val KEY_CURRENT_TOKEN = "current_token"
    private const val KEY_REGISTRATION_DATE = "registration_date"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Verifica si el token ya fue registrado exitosamente
     */
    fun isTokenRegistered(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TOKEN_REGISTERED, false)
    }

    /**
     * Marca el token como registrado
     */
    fun setTokenRegistered(context: Context, registered: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_TOKEN_REGISTERED, registered)
            .putLong(KEY_REGISTRATION_DATE, System.currentTimeMillis())
            .apply()
    }

    /**
     * Obtiene el token FCM actual guardado
     */
    fun getCurrentToken(context: Context): String? {
        return getPrefs(context).getString(KEY_CURRENT_TOKEN, null)
    }

    /**
     * Guarda el token FCM actual
     */
    fun saveCurrentToken(context: Context, token: String) {
        getPrefs(context).edit()
            .putString(KEY_CURRENT_TOKEN, token)
            .apply()
    }

    /**
     * Obtiene la fecha de registro del token
     */
    fun getRegistrationDate(context: Context): Long {
        return getPrefs(context).getLong(KEY_REGISTRATION_DATE, 0L)
    }

    /**
     * Limpia todos los datos relacionados con el token
     */
    fun clearTokenData(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_TOKEN_REGISTERED)
            .remove(KEY_CURRENT_TOKEN)
            .remove(KEY_REGISTRATION_DATE)
            .apply()
    }

    /**
     * Limpia completamente todas las preferencias de FCM
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
