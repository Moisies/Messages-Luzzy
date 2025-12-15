package com.goodwy.smsmessenger.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Servicio de Firebase Cloud Messaging
 * Maneja la recepción de mensajes push y la actualización del token
 */
class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM_Service"
    }

    /**
     * Se llama cuando se recibe un nuevo token FCM
     * Esto puede ocurrir cuando:
     * - La app se instala por primera vez
     * - El usuario reinstala la app
     * - El usuario limpia los datos de la app
     * - El token expira
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM recibido: ${token.take(20)}...")

        // Registrar el nuevo token
        FCMManager.registerToken(applicationContext, token)
    }

    /**
     * Se llama cuando se recibe un mensaje de Firebase
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "Mensaje recibido de: ${message.from}")

        // Manejar mensaje de datos
        if (message.data.isNotEmpty()) {
            Log.d(TAG, "Datos del mensaje: ${message.data}")
            handleDataMessage(message.data)
        }

        // Manejar notificación
        message.notification?.let {
            Log.d(TAG, "Título de notificación: ${it.title}")
            Log.d(TAG, "Cuerpo de notificación: ${it.body}")
            showNotification(it.title, it.body)
        }
    }

    /**
     * Maneja mensajes que contienen datos personalizados
     */
    private fun handleDataMessage(data: Map<String, String>) {
        // Aquí puedes manejar diferentes tipos de mensajes según tus necesidades
        // Por ejemplo:
        // - Actualizar contactos
        // - Sincronizar mensajes
        // - Mostrar notificaciones personalizadas
        val type = data["type"]
        Log.d(TAG, "Tipo de mensaje: $type")

        when (type) {
            "new_message" -> {
                // Manejar nuevo mensaje SMS
                val from = data["from"]
                val body = data["body"]
                Log.d(TAG, "Nuevo mensaje de $from: $body")
            }
            "sync" -> {
                // Sincronizar datos
                Log.d(TAG, "Solicitud de sincronización recibida")
            }
            else -> {
                Log.d(TAG, "Tipo de mensaje desconocido: $type")
            }
        }
    }

    /**
     * Muestra una notificación al usuario
     */
    private fun showNotification(title: String?, body: String?) {
        // Por ahora solo logueamos
        // En producción, aquí crearías y mostrarías una NotificationCompat
        Log.d(TAG, "Mostrar notificación - Título: $title, Cuerpo: $body")

        // TODO: Implementar NotificationManager para mostrar notificaciones
        // val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // ... crear y mostrar notificación
    }

    /**
     * Se llama cuando se elimina un mensaje porque no pudo ser entregado
     */
    override fun onDeletedMessages() {
        super.onDeletedMessages()
        Log.w(TAG, "Mensajes eliminados del servidor")
    }

    /**
     * Se llama cuando hay un error al enviar un mensaje
     */
    override fun onMessageSent(msgId: String) {
        super.onMessageSent(msgId)
        Log.d(TAG, "Mensaje enviado: $msgId")
    }

    /**
     * Se llama cuando falla el envío de un mensaje
     */
    override fun onSendError(msgId: String, exception: Exception) {
        super.onSendError(msgId, exception)
        Log.e(TAG, "Error al enviar mensaje $msgId", exception)
    }
}
