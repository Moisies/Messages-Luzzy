package com.goodwy.smsmessenger.fcm

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.*
import com.goodwy.smsmessenger.BuildConfig
import com.goodwy.smsmessenger.network.RetrofitClient
import com.goodwy.smsmessenger.network.models.DeviceRegistrationRequest
import com.goodwy.smsmessenger.utils.SharedPrefsManager
import java.util.concurrent.TimeUnit

/**
 * Worker que se encarga de registrar el token FCM en el servidor
 * con reintentos automáticos usando backoff exponencial
 */
class FCMRegistrationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "FCM_RegistrationWorker"
        private const val WORK_NAME = "fcm_registration_work"
        private const val INPUT_TOKEN = "fcm_token"
        private const val MAX_RETRIES = 5

        /**
         * Encola el trabajo de registro del token
         */
        fun enqueue(context: Context, token: String) {
            val inputData = Data.Builder()
                .putString(INPUT_TOKEN, token)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val registrationWork = OneTimeWorkRequestBuilder<FCMRegistrationWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    registrationWork
                )

            Log.d(TAG, "Trabajo de registro encolado para token: ${token.take(20)}...")
        }
    }

    override suspend fun doWork(): Result {
        val token = inputData.getString(INPUT_TOKEN) ?: return Result.failure()

        Log.d(TAG, "Iniciando registro de token (intento ${runAttemptCount + 1}/$MAX_RETRIES)")

        return try {
            // Obtener información del dispositivo
            val deviceInfo = getDeviceInfo()

            // Crear request
            val request = DeviceRegistrationRequest(
                fcmToken = token,
                deviceId = deviceInfo["device_id"],
                deviceName = deviceInfo["device_name"],
                deviceModel = deviceInfo["device_model"],
                osVersion = deviceInfo["os_version"],
                appVersion = deviceInfo["app_version"]
            )

            Log.d(TAG, "Enviando registro al servidor...")

            // Llamar a la API
            val response = RetrofitClient.apiService.registerDevice(request)

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "✓ Token registrado exitosamente")
                Log.d(TAG, "Respuesta: ${response.body()?.message}")

                // Guardar flag de registro exitoso
                SharedPrefsManager.setTokenRegistered(applicationContext, true)
                SharedPrefsManager.saveCurrentToken(applicationContext, token)

                Result.success()
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "✗ Error del servidor: ${response.code()} - $errorBody")

                // Reintentar si aún hay intentos disponibles
                if (runAttemptCount < MAX_RETRIES) {
                    Log.w(TAG, "Reintentando... (${runAttemptCount + 1}/$MAX_RETRIES)")
                    Result.retry()
                } else {
                    Log.e(TAG, "Máximo de reintentos alcanzado")
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Excepción al registrar token", e)

            // Reintentar si aún hay intentos disponibles
            if (runAttemptCount < MAX_RETRIES) {
                Log.w(TAG, "Reintentando debido a excepción... (${runAttemptCount + 1}/$MAX_RETRIES)")
                Result.retry()
            } else {
                Log.e(TAG, "Máximo de reintentos alcanzado después de excepción")
                Result.failure()
            }
        }
    }

    /**
     * Obtiene información del dispositivo
     */
    private fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "device_id" to getDeviceId(),
            "device_name" to getDeviceName(),
            "device_model" to Build.MODEL,
            "os_version" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "app_version" to BuildConfig.VERSION_NAME
        )
    }

    /**
     * Genera un ID único para el dispositivo
     */
    private fun getDeviceId(): String {
        // Obtener o generar un ID único del dispositivo
        val prefs = applicationContext.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)

        if (deviceId == null) {
            deviceId = "${Build.MANUFACTURER}_${Build.MODEL}_${System.currentTimeMillis()}"
                .replace(" ", "_")
                .lowercase()
            prefs.edit().putString("device_id", deviceId).apply()
        }

        return deviceId
    }

    /**
     * Obtiene el nombre del dispositivo
     */
    private fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    }
}
