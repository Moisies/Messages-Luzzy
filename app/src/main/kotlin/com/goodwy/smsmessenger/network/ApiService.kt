package com.goodwy.smsmessenger.network

import com.goodwy.smsmessenger.network.models.ApiResponse
import com.goodwy.smsmessenger.network.models.DeviceData
import com.goodwy.smsmessenger.network.models.DeviceRegistrationRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Interface que define los endpoints de la API
 */
interface ApiService {

    /**
     * Registra o actualiza un dispositivo con su token FCM
     * POST https://luzzy.app/api/dispositivo/registro
     */
    @POST("dispositivo/registro")
    suspend fun registerDevice(
        @Body request: DeviceRegistrationRequest
    ): Response<ApiResponse<DeviceData>>

    /**
     * Desactiva un dispositivo (al cerrar sesión o desinstalar)
     * POST https://luzzy.app/api/dispositivo/desactivar
     */
    @POST("dispositivo/desactivar")
    suspend fun deactivateDevice(
        @Body request: Map<String, String>
    ): Response<ApiResponse<Unit>>

    // Aquí puedes agregar más endpoints según necesites
    // Por ejemplo:

    /**
     * Obtener mensajes del servidor
     */
    // @GET("messages")
    // suspend fun getMessages(): Response<ApiResponse<List<Message>>>

    /**
     * Enviar un mensaje
     */
    // @POST("messages/send")
    // suspend fun sendMessage(@Body message: SendMessageRequest): Response<ApiResponse<Message>>
}
