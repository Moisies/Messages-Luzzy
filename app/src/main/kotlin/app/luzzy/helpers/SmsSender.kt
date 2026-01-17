package app.luzzy.helpers

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

class SmsSender(private val context: Context) {

    companion object {
        private const val TAG = "SmsSender"
        private val recentlySent = mutableMapOf<String, Long>()
        private const val SEND_COOLDOWN_MS = 5000L
    }

    fun sendSms(recipient: String, message: String): Boolean {
        Log.d(TAG, "📤 Intentando enviar SMS a: $recipient")

        val smsHash = "$recipient:$message"
        val currentTime = System.currentTimeMillis()

        synchronized(recentlySent) {
            val lastSentTime = recentlySent[smsHash]
            if (lastSentTime != null && (currentTime - lastSentTime) < SEND_COOLDOWN_MS) {
                val timeDiff = currentTime - lastSentTime
                Log.w(TAG, "🚫 SMS DUPLICADO bloqueado (enviado hace ${timeDiff}ms) - $recipient")
                return true
            }
            recentlySent[smsHash] = currentTime

            recentlySent.entries.removeIf { (currentTime - it.value) > SEND_COOLDOWN_MS }
        }

        if (!hasPermissions()) {
            Log.e(TAG, "✗ FALLO: No hay permiso SEND_SMS")
            return false
        }

        Log.d(TAG, "✓ Permiso SEND_SMS confirmado")

        return try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            if (message.length > 160) {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(
                    recipient,
                    null,
                    parts,
                    null,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    recipient,
                    null,
                    message,
                    null,
                    null
                )
            }

            saveToSentMessages(recipient, message)

            Log.d(TAG, "SMS enviado exitosamente a $recipient")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar SMS a $recipient", e)
            false
        }
    }

    private fun saveToSentMessages(recipient: String, message: String) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, recipient)
                put(Telephony.Sms.BODY, message)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }

            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            Log.d(TAG, "SMS guardado en historial de enviados")

            val intent = Intent("android.provider.Telephony.SMS_SENT")
            context.sendBroadcast(intent)
            Log.d(TAG, "Broadcast enviado para actualizar UI")
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar SMS en historial", e)
        }
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
