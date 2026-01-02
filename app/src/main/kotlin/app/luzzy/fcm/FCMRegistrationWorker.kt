package app.luzzy.fcm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.*
import app.luzzy.network.RetrofitClient
import app.luzzy.network.models.RegisterDeviceRequest
import app.luzzy.utils.SharedPrefsManager
import java.util.concurrent.TimeUnit

class FCMRegistrationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "FCM_RegistrationWorker"
        private const val WORK_NAME = "fcm_registration_work"
        private const val INPUT_TOKEN = "fcm_token"
        private const val MAX_RETRIES = 5

        fun enqueue(context: Context, token: String?) {
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

            Log.d(TAG, "Trabajo de registro encolado para token: ${token?.take(20) ?: "null"}")
        }
    }

    override suspend fun doWork(): Result {
        val token = inputData.getString(INPUT_TOKEN) ?: return Result.failure()

        Log.d(TAG, "Iniciando registro de token (intento ${runAttemptCount + 1}/$MAX_RETRIES)")

        return try {
            val phone = getPhoneNumber()

            val request = RegisterDeviceRequest(
                phone = phone,
                registrationToken = token
            )

            Log.d(TAG, "Enviando registro al servidor para phone: $phone")

            val response = RetrofitClient.apiService.registerDevice(request)

            if (response.isSuccessful && response.body() != null) {
                val authToken = response.body()!!.token
                Log.d(TAG, "✓ Token registrado exitosamente")

                SharedPrefsManager.setTokenRegistered(applicationContext, true)
                SharedPrefsManager.saveCurrentToken(applicationContext, token)
                SharedPrefsManager.saveAuthToken(applicationContext, authToken)
                SharedPrefsManager.savePhoneNumber(applicationContext, phone)

                Result.success()
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "✗ Error del servidor: ${response.code()} - $errorBody")

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

            if (runAttemptCount < MAX_RETRIES) {
                Log.w(TAG, "Reintentando debido a excepción... (${runAttemptCount + 1}/$MAX_RETRIES)")
                Result.retry()
            } else {
                Log.e(TAG, "Máximo de reintentos alcanzado después de excepción")
                Result.failure()
            }
        }
    }

    private fun getPhoneNumber(): String {
        val prefs = applicationContext.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        var phone = prefs.getString("user_phone", null)

        if (phone == null) {
            phone = try {
                if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) {
                    val telephonyManager = applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    telephonyManager.line1Number?.takeIf { it.isNotEmpty() } ?: generatePhoneFromDevice()
                } else {
                    generatePhoneFromDevice()
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo obtener número de teléfono, usando generado", e)
                generatePhoneFromDevice()
            }

            prefs.edit().putString("user_phone", phone).apply()
        }

        return phone
    }

    private fun generatePhoneFromDevice(): String {
        val prefs = applicationContext.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        val savedPhone = prefs.getString("generated_phone", null)

        if (savedPhone != null) {
            return savedPhone
        }

        val generatedPhone = "+${System.currentTimeMillis() % 1000000000000}"
        prefs.edit().putString("generated_phone", generatedPhone).apply()
        return generatedPhone
    }
}
