package app.luzzy.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import app.luzzy.R
import app.luzzy.network.RetrofitClient
import app.luzzy.network.models.SendMessagesRequest
import app.luzzy.network.models.Message
import app.luzzy.network.models.RegisterDeviceRequest
import app.luzzy.utils.SharedPrefsManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class SmsUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SmsUploadWorker"
        private const val WORK_NAME = "sms_upload_work"
        private const val MAX_RETRIES = 5
        private const val CHANNEL_ID = "sms_upload_failures"
        private const val NOTIFICATION_ID = 1001

        private const val INPUT_SMS_ID = "sms_id"
        private const val INPUT_FROM = "from"
        private const val INPUT_TO = "to"
        private const val INPUT_BODY = "body"
        private const val INPUT_TIMESTAMP = "timestamp"

        fun enqueue(
            context: Context,
            smsId: Long,
            from: String,
            to: String,
            body: String,
            timestamp: Long
        ) {
            val inputData = Data.Builder()
                .putLong(INPUT_SMS_ID, smsId)
                .putString(INPUT_FROM, from)
                .putString(INPUT_TO, to)
                .putString(INPUT_BODY, body)
                .putLong(INPUT_TIMESTAMP, timestamp)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val uploadWork = OneTimeWorkRequestBuilder<SmsUploadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "$WORK_NAME-$smsId",
                    ExistingWorkPolicy.REPLACE,
                    uploadWork
                )

            Log.d(TAG, "SMS upload work encolado para SMS ID: $smsId")
        }
    }

    override suspend fun doWork(): Result {
        val smsId = inputData.getLong(INPUT_SMS_ID, -1L)
        val from = inputData.getString(INPUT_FROM) ?: return Result.failure()
        val to = inputData.getString(INPUT_TO) ?: return Result.failure()
        val body = inputData.getString(INPUT_BODY) ?: return Result.failure()
        val timestamp = inputData.getLong(INPUT_TIMESTAMP, 0L)

        Log.d(TAG, "Procesando SMS ID: $smsId (intento ${runAttemptCount + 1}/$MAX_RETRIES)")

        return try {
            val authHeader = SharedPrefsManager.getAuthHeader(applicationContext)
            if (authHeader == null) {
                Log.e(TAG, "=== ERROR: No auth token disponible. El usuario debe hacer login con Google primero ===")
                handleFailure(timestamp, from)
                return Result.failure()
            }

            Log.d(TAG, "=== Auth token OK, enviando al servidor ===")
            Log.d(TAG, "=== URL: ${RetrofitClient.apiService.javaClass} ===")

            val smsRepository = SmsRepository(applicationContext)
            val history = smsRepository.getSmsHistory36Hours(from)

            val allMessages = mutableListOf<Message>()

            history.forEach { smsMsg ->
                allMessages.add(Message(
                    from = smsMsg.from,
                    message = smsMsg.body,
                    timestamp = smsMsg.timestamp.toString()
                ))
            }

            allMessages.add(Message(
                from = from,
                message = body,
                timestamp = timestamp.toString()
            ))

            val request = SendMessagesRequest(
                from = from,
                to = to,
                messages = allMessages
            )

            Log.d(TAG, "Enviando SMS al servidor con ${allMessages.size} mensajes (historial + nuevo)")

            val response = RetrofitClient.apiService.sendMessages(authHeader, request)

            if (response.isSuccessful) {
                Log.d(TAG, "=== ✅ SMS enviado exitosamente al servidor ===")
                smsRepository.markAsRead(timestamp)
                Result.success()
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "=== ❌ Error del servidor: ${response.code()} ===")
                Log.e(TAG, "=== Error body: $errorBody ===")

                if (response.code() == 401) {
                    Log.w(TAG, "Error 401 detectado, regenerando token JWT...")
                    regenerateAuthToken()
                }

                if (runAttemptCount < MAX_RETRIES) {
                    Log.w(TAG, "Reintentando silenciosamente... (${runAttemptCount + 1}/$MAX_RETRIES)")
                    Result.retry()
                } else {
                    Log.e(TAG, "=== ❌ Máximo de reintentos alcanzado ===")
                    handleFailure(timestamp, from)
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al enviar SMS", e)

            if (runAttemptCount < MAX_RETRIES) {
                Log.w(TAG, "Reintentando silenciosamente debido a excepción... (${runAttemptCount + 1}/$MAX_RETRIES)")
                Result.retry()
            } else {
                Log.e(TAG, "Máximo de reintentos alcanzado después de excepción")
                handleFailure(timestamp, from)
                Result.failure()
            }
        }
    }

    private suspend fun regenerateAuthToken() {
        try {

            val fcmToken = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "Token FCM obtenido para regeneración")

            val phone = SharedPrefsManager.getPhoneNumber(applicationContext) ?: "unknown"

            val request = RegisterDeviceRequest(
                phone = phone,
                registrationToken = fcmToken
            )

            val response = RetrofitClient.apiService.registerDevice(request)

            if (response.isSuccessful && response.body() != null) {
                val newAuthToken = response.body()!!.token
                SharedPrefsManager.saveAuthToken(applicationContext, newAuthToken)
                Log.d(TAG, "✅ Token JWT regenerado exitosamente")
            } else {
                Log.e(TAG, "❌ Error al regenerar token: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción al regenerar token", e)
        }
    }

    private fun handleFailure(timestamp: Long, from: String) {
        val smsRepository = SmsRepository(applicationContext)
        smsRepository.markAsUnread(timestamp)

        showFailureNotification(from)

        Log.d(TAG, "SMS con timestamp $timestamp marcado como no leído y notificación mostrada")
    }

    private fun showFailureNotification(from: String) {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_messages)
            .setContentTitle(applicationContext.getString(R.string.sms_upload_failed_title))
            .setContentText(applicationContext.getString(R.string.sms_upload_failed_body, from))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = applicationContext.getString(R.string.sms_upload_channel_name)
            val descriptionText = applicationContext.getString(R.string.sms_upload_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
