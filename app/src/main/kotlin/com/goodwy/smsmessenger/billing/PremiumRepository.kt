package com.goodwy.smsmessenger.billing

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PremiumRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            BillingConstants.PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences(BillingConstants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPremium(): Boolean {
        return prefs.getBoolean(BillingConstants.PREF_IS_PREMIUM, false)
    }

    fun setPremium(isPremium: Boolean, purchaseToken: String? = null) {
        prefs.edit().apply {
            putBoolean(BillingConstants.PREF_IS_PREMIUM, isPremium)
            if (purchaseToken != null) {
                putString(BillingConstants.PREF_PURCHASE_TOKEN, purchaseToken)
                putLong(BillingConstants.PREF_PURCHASE_TIME, System.currentTimeMillis())
            } else if (!isPremium) {
                remove(BillingConstants.PREF_PURCHASE_TOKEN)
                remove(BillingConstants.PREF_PURCHASE_TIME)
            }
            apply()
        }
    }

    fun getPurchaseToken(): String? {
        return prefs.getString(BillingConstants.PREF_PURCHASE_TOKEN, null)
    }

    fun getPurchaseTime(): Long {
        return prefs.getLong(BillingConstants.PREF_PURCHASE_TIME, 0L)
    }

    fun clearPremiumData() {
        prefs.edit().clear().apply()
    }
}
