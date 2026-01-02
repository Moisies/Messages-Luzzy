package app.luzzy.fcm

import android.content.Context
import android.util.Log
import app.luzzy.utils.SharedPrefsManager
import com.google.firebase.messaging.FirebaseMessaging

object FCMManager {

    private const val TAG = "FCM_Manager"

    fun initialize(context: Context) {
        Log.d(TAG, "Inicializando FCM Manager...")

        val isFirstLaunch = !SharedPrefsManager.isTokenRegistered(context)

        if (isFirstLaunch) {
            Log.d(TAG, "Primer lanzamiento detectado - Obteniendo token FCM...")
            getTokenAndRegister(context)
        } else {
            Log.d(TAG, "Token ya registrado previamente")

            verifyCurrentToken(context)
        }
    }

    private fun getTokenAndRegister(context: Context) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d(TAG, "✓ Token FCM obtenido: ${token.take(20)}...")
                registerToken(context, token)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "✗ Error al obtener token FCM", exception)
                Log.w(TAG, "⚠️ Registrando con token temporal (auto-respuesta no funcionará)")

                val tempToken = "temp_fcm_${System.currentTimeMillis()}_${android.os.Build.MODEL.hashCode()}"
                registerToken(context, tempToken)
            }
    }

    private fun verifyCurrentToken(context: Context) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { currentToken ->
                val savedToken = SharedPrefsManager.getCurrentToken(context)

                if (currentToken != savedToken) {
                    Log.d(TAG, "Token FCM ha cambiado - Actualizando...")
                    registerToken(context, currentToken)
                } else {
                    Log.d(TAG, "Token FCM actual coincide con el guardado")
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al verificar token actual", exception)
                Log.w(TAG, "⚠️ Registrando con token temporal (auto-respuesta no funcionará)")

                val tempToken = "temp_fcm_${System.currentTimeMillis()}_${android.os.Build.MODEL.hashCode()}"
                registerToken(context, tempToken)
            }
    }

    fun registerToken(context: Context, token: String) {
        Log.d(TAG, "Registrando token: ${token.take(20)}...")

        FCMRegistrationWorker.enqueue(context, token)
    }

    fun unregisterToken(context: Context) {
        Log.d(TAG, "Desregistrando token FCM...")

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->

                Log.d(TAG, "Token a desregistrar: ${token.take(20)}...")

                SharedPrefsManager.clearTokenData(context)

                Log.d(TAG, "✓ Token desregistrado localmente")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "✗ Error al desregistrar token", exception)
            }
    }

    fun deleteToken(context: Context) {
        Log.d(TAG, "Eliminando token FCM...")

        FirebaseMessaging.getInstance().deleteToken()
            .addOnSuccessListener {
                Log.d(TAG, "✓ Token FCM eliminado exitosamente")
                SharedPrefsManager.clearTokenData(context)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "✗ Error al eliminar token FCM", exception)
            }
    }

    fun subscribeToTopic(topic: String) {
        Log.d(TAG, "Suscribiendo a tópico: $topic")

        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnSuccessListener {
                Log.d(TAG, "✓ Suscrito exitosamente al tópico: $topic")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "✗ Error al suscribirse al tópico: $topic", exception)
            }
    }

    fun unsubscribeFromTopic(topic: String) {
        Log.d(TAG, "Desuscribiendo del tópico: $topic")

        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            .addOnSuccessListener {
                Log.d(TAG, "✓ Desuscrito exitosamente del tópico: $topic")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "✗ Error al desuscribirse del tópico: $topic", exception)
            }
    }
}
