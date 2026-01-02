package app.luzzy.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import app.luzzy.network.models.SmsMessage

class SmsRepository(private val context: Context) {

    companion object {
        private const val TAG = "SmsRepository"
        private const val HOURS_36_IN_MILLIS = 36 * 60 * 60 * 1000L
    }

    fun getSmsHistory36Hours(contactNumber: String): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val cutoffTime = System.currentTimeMillis() - HOURS_36_IN_MILLIS

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val selection = "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(contactNumber, cutoffTime.toString())
        val sortOrder = "${Telephony.Sms.DATE} ASC"

        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIndex)
                    val body = cursor.getString(bodyIndex)
                    val date = cursor.getLong(dateIndex)
                    val type = cursor.getInt(typeIndex)

                    val smsMessage = when (type) {
                        Telephony.Sms.MESSAGE_TYPE_INBOX -> {
                            SmsMessage(
                                from = address,
                                to = getDevicePhoneNumber(),
                                body = body,
                                timestamp = date,
                                type = "received"
                            )
                        }
                        Telephony.Sms.MESSAGE_TYPE_SENT -> {
                            SmsMessage(
                                from = getDevicePhoneNumber(),
                                to = address,
                                body = body,
                                timestamp = date,
                                type = "sent"
                            )
                        }
                        else -> null
                    }

                    smsMessage?.let { messages.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo historial de SMS", e)
        }

        return messages
    }

    fun markAsRead(timestamp: Long) {
        try {
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }

            val selection = "${Telephony.Sms.DATE} = ?"
            val selectionArgs = arrayOf(timestamp.toString())

            val updated = context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                selection,
                selectionArgs
            )

            Log.d(TAG, "SMS con timestamp $timestamp marcado como leído ($updated filas actualizadas)")
        } catch (e: Exception) {
            Log.e(TAG, "Error marcando SMS como leído", e)
        }
    }

    fun markAsUnread(timestamp: Long) {
        try {
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.READ, 0)
            }

            val selection = "${Telephony.Sms.DATE} = ?"
            val selectionArgs = arrayOf(timestamp.toString())

            val updated = context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                selection,
                selectionArgs
            )

            Log.d(TAG, "SMS con timestamp $timestamp marcado como no leído ($updated filas actualizadas)")
        } catch (e: Exception) {
            Log.e(TAG, "Error marcando SMS como no leído", e)
        }
    }

    private fun getDevicePhoneNumber(): String {
        val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        return prefs.getString("user_phone", "unknown") ?: "unknown"
    }
}
