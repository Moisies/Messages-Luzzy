package app.luzzy.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import app.luzzy.R
import app.luzzy.utils.SharedPrefsManager
import kotlinx.coroutines.tasks.await

class GoogleAuthRepository(private val context: Context) {

    companion object {
        private const val TAG = "GoogleAuthRepository"
        private const val PREFS_NAME = "google_auth_prefs"
        private const val KEY_GOOGLE_TOKEN = "google_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_PHOTO_URL = "photo_url"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(context.getString(R.string.google_client_id))
            .build()

        return GoogleSignIn.getClient(context, gso)
    }

    fun saveGoogleAuthData(
        token: String,
        email: String,
        displayName: String?,
        photoUrl: String?
    ) {
        sharedPreferences.edit().apply {
            putString(KEY_GOOGLE_TOKEN, token)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_DISPLAY_NAME, displayName)
            putString(KEY_PHOTO_URL, photoUrl)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    fun getToken(): String? {
        return sharedPreferences.getString(KEY_GOOGLE_TOKEN, null)
    }

    fun getAuthHeader(): String? {
        val token = getToken()
        return if (token != null) "Bearer $token" else null
    }

    fun getUserEmail(): String? {
        return sharedPreferences.getString(KEY_USER_EMAIL, null)
    }

    fun getDisplayName(): String? {
        return sharedPreferences.getString(KEY_DISPLAY_NAME, null)
    }

    fun getPhotoUrl(): String? {
        return sharedPreferences.getString(KEY_PHOTO_URL, null)
    }

    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun clearAuthData() {
        sharedPreferences.edit().clear().apply()
    }

    suspend fun logout(revokeAccess: Boolean = false): Boolean {
        return try {
            val googleSignInClient = getGoogleSignInClient()

            if (revokeAccess) {

                Log.d(TAG, "Revocando acceso completo de Google...")
                googleSignInClient.revokeAccess().await()
            } else {

                Log.d(TAG, "Cerrando sesión de Google...")
                googleSignInClient.signOut().await()
            }

            clearAuthData()

            SharedPrefsManager.saveAuthToken(context, "")

            Log.d(TAG, "Logout exitoso")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar sesión de Google", e)

            clearAuthData()
            SharedPrefsManager.saveAuthToken(context, "")
            false
        }
    }
}
