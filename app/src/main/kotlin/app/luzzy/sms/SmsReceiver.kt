package app.luzzy.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        Log.d(TAG, "SMS recibido, procesando...")

        val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } else {
            getSmsMessagesLegacy(intent)
        }

        if (messages.isEmpty()) {
            Log.w(TAG, "No se pudo extraer mensajes del intent")
            return
        }

        for (message in messages) {
            val from = message.displayOriginatingAddress ?: message.originatingAddress ?: continue
            val body = message.messageBody ?: continue
            val timestamp = message.timestampMillis

            Log.d(TAG, "SMS de: $from, timestamp: $timestamp")

            val smsId = timestamp

            val devicePhone = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                .getString("user_phone", "unknown") ?: "unknown"

            SmsUploadWorker.enqueue(
                context = context,
                smsId = smsId,
                from = from,
                to = devicePhone,
                body = body,
                timestamp = timestamp
            )

            Log.d(TAG, "Worker encolado para enviar SMS al servidor (ID: $smsId)")
        }
    }

    private fun getSmsMessagesLegacy(intent: Intent): Array<android.telephony.SmsMessage> {
        val bundle = intent.extras ?: return emptyArray()
        val pdusObj = bundle.get("pdus") as? Array<*> ?: return emptyArray()

        val messages = mutableListOf<android.telephony.SmsMessage>()
        for (pdu in pdusObj) {
            val message = android.telephony.SmsMessage.createFromPdu(pdu as ByteArray)
            messages.add(message)
        }

        return messages.toTypedArray()
    }
}
