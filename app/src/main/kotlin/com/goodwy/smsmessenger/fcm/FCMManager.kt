package com.goodwy.smsmessenger.fcm

import android.content.Context
import android.util.Log
import com.goodwy.smsmessenger.utils.SharedPrefsManager
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Manager centralizado para gestionar Firebase Cloud Messaging
 */
object FCMManager {

    private const val TAG = "FCM_Manager"

    /**
     * Inicializa FCM y registra el token en el primer lanzamiento
     * @param context Contexto de la aplicación
     */
    fun initialize(context: Context) {
        Log.d(TAG, "Inicializando FCM Manager...")

        // Verificar si es el primer lanzamiento
        val isFirstLaunch = !SharedPrefsManager.isTokenRegistered(context)

        if (isFirstLaunch) {
            Log.d(TAG, "Primer lanzamiento detectado - Obteniendo token FCM...")
            getTokenAndRegister(context)
        } else {
            Log.d(TAG, "Token ya registrado previamente")
            // Verificar si el token actual sigue siendo el mismo
            verifyCurrentToken(context)
        }
    }

    /**
     * Obtiene el token FCM actual y lo registra
     * @param context Contexto de la aplicación
     */
    private fun getTokenAndRegister(context: Context) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d(TAG, "✓ Token FCM obtenido: ${token.take(20)}...")
                registerToken(context, token)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "✗ Error al obtener token FCM", exception)
            }
    }

    /**
     * Verifica si el token actual es diferente al guardado
     * Si es diferente, lo actualiza
     */
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
            }
    }

    /**
     * Registra un token FCM en el servidor
     * Utiliza WorkManager para reintentos automáticos
     * @param context Contexto de la aplicación
     * @param token Token FCM a registrar
     */
    fun registerToken(context: Context, token: String) {
        Log.d(TAG, "Registrando token: ${token.take(20)}...")

        // Encolar trabajo de registro con WorkManager
        FCMRegistrationWorker.enqueue(context, token)
    }

    /**
     * Cancela el registro del token (útil al cerrar sesión)
     * @param context Contexto de la aplicación
     */
    fun unregisterToken(context: Context) {
        Log.d(TAG, "Desregistrando token FCM...")

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                // Aquí podrías llamar a un endpoint para desactivar el token
                // Por ejemplo: POST /api/dispositivo/desactivar
                Log.d(TAG, "Token a desregistrar: ${token.take(20)}...")

                // Limpiar SharedPreferences
                SharedPrefsManager.clearTokenData(context)

                Log.d(TAG, "✓ Token desregistrado localmente")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "✗ Error al desregistrar token", exception)
            }
    }

    /**
     * Elimina el token FCM actual
     * Útil para pruebas o cuando el usuario cierra sesión completamente
     */
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

    /**
     * Suscribe el dispositivo a un tópico de Firebase
     * Útil para enviar notificaciones a grupos de usuarios
     * @param topic Nombre del tópico (ej: "noticias", "promociones")
     */
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

    /**
     * Desuscribe el dispositivo de un tópico
     * @param topic Nombre del tópico
     */
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
