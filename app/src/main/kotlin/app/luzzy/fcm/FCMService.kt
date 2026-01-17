package app.luzzy.fcm

import android.util.Log
import app.luzzy.extensions.getThreadId
import app.luzzy.helpers.ContactSendModeRepository
import app.luzzy.helpers.DraftManager
import app.luzzy.helpers.SmsSender
import app.luzzy.models.SendMode
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM_Service"
        private val processedMessages = mutableMapOf<String, Long>()
        private const val MAX_CACHE_SIZE = 100
        private const val DUPLICATE_WINDOW_MS = 60000L
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM recibido: ${token.take(20)}...")

        FCMManager.registerToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "Mensaje recibido de: ${message.from}")

        if (message.data.isNotEmpty()) {
            Log.d(TAG, "Datos del mensaje: ${message.data}")
            handleDataMessage(message.data)
        }

        message.notification?.let {
            Log.d(TAG, "Título de notificación: ${it.title}")
            Log.d(TAG, "Cuerpo de notificación: ${it.body}")
            showNotification(it.title, it.body)
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val recipient = data["to"]
        val message = data["message"]

        if (recipient.isNullOrBlank() || message.isNullOrBlank()) {
            Log.w(TAG, "Payload inválido: to o message están vacíos")
            return
        }

        val messageHash = "$recipient:$message"
        val currentTime = System.currentTimeMillis()

        val shouldProcess = synchronized(processedMessages) {
            val lastProcessedTime = processedMessages[messageHash]

            if (lastProcessedTime != null && (currentTime - lastProcessedTime) < DUPLICATE_WINDOW_MS) {
                val timeDiff = currentTime - lastProcessedTime
                Log.w(TAG, "🚫 DUPLICADO bloqueado (${timeDiff}ms desde último) - Destinatario: $recipient")
                false
            } else {
                processedMessages[messageHash] = currentTime
                Log.d(TAG, "✅ Mensaje marcado como procesado - Hash: ${messageHash.hashCode()}")

                processedMessages.entries.removeIf { (currentTime - it.value) > DUPLICATE_WINDOW_MS }

                if (processedMessages.size > MAX_CACHE_SIZE) {
                    val sortedEntries = processedMessages.entries.sortedBy { it.value }
                    sortedEntries.take(processedMessages.size - MAX_CACHE_SIZE / 2).forEach {
                        processedMessages.remove(it.key)
                    }
                }
                true
            }
        }

        if (!shouldProcess) {
            return
        }

        Log.d(TAG, "⚙️ Procesando mensaje FCM para $recipient")

        val sendModeRepository = ContactSendModeRepository(applicationContext)
        val threadId = getThreadIdForRecipient(recipient)

        if (threadId == 0L) {
            Log.w(TAG, "⚠️ No se pudo obtener threadId para $recipient, usando modo ENVÍO por defecto")
            sendSmsDirectly(recipient, message)
            return
        }

        val sendMode = sendModeRepository.getSendMode(threadId)
        Log.d(TAG, "📋 ThreadID: $threadId | Modo configurado: $sendMode")

        when (sendMode) {
            SendMode.SEND -> {
                Log.d(TAG, "✉️ Modo ENVÍO activado → Enviando SMS automáticamente")
                sendSmsDirectly(recipient, message)
            }
            SendMode.DRAFT -> {
                Log.d(TAG, "📝 Modo BORRADOR activado → Mostrando notificación")
                saveDraftAndNotify(recipient, message)
            }
        }
    }

    private fun sendSmsDirectly(recipient: String, message: String) {
        val smsSender = SmsSender(applicationContext)
        val success = smsSender.sendSms(recipient, message)

        if (success) {
            Log.d(TAG, "✓ SMS enviado exitosamente a $recipient")
        } else {
            Log.e(TAG, "✗ FALLO al enviar SMS a $recipient")
            Log.e(TAG, "Verifica: 1) Permiso SEND_SMS, 2) App es SMS predeterminada")
        }
    }

    private fun saveDraftAndNotify(recipient: String, message: String) {
        val draftManager = DraftManager(applicationContext)
        draftManager.saveDraftAndNotify(recipient, message)
        Log.d(TAG, "Borrador guardado y notificación enviada para $recipient")
    }

    private fun getThreadIdForRecipient(recipient: String): Long {
        return try {
            applicationContext.getThreadId(recipient)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener threadId para $recipient", e)
            0L
        }
    }

    private fun showNotification(title: String?, body: String?) {

        Log.d(TAG, "Mostrar notificación - Título: $title, Cuerpo: $body")

    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        Log.w(TAG, "Mensajes eliminados del servidor")
    }

    override fun onMessageSent(msgId: String) {
        super.onMessageSent(msgId)
        Log.d(TAG, "Mensaje enviado: $msgId")
    }

    override fun onSendError(msgId: String, exception: Exception) {
        super.onSendError(msgId, exception)
        Log.e(TAG, "Error al enviar mensaje $msgId", exception)
    }
}
