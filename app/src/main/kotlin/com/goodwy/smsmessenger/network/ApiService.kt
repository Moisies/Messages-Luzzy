package com.goodwy.smsmessenger.network

import com.goodwy.smsmessenger.network.models.ApiResponse
import com.goodwy.smsmessenger.network.models.AuthResponse
import com.goodwy.smsmessenger.network.models.ConfiguracionResponse
import com.goodwy.smsmessenger.network.models.ConfiguracionUpdateRequest
import com.goodwy.smsmessenger.network.models.DeviceData
import com.goodwy.smsmessenger.network.models.DeviceRegistrationRequest
import com.goodwy.smsmessenger.network.models.LoginRequest
import com.goodwy.smsmessenger.network.models.PurchaseData
import com.goodwy.smsmessenger.network.models.PurchaseRegistrationRequest
import com.goodwy.smsmessenger.network.models.PurchaseStatusResponse
import com.goodwy.smsmessenger.network.models.RegisterRequest
import com.goodwy.smsmessenger.network.models.UserData
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<ApiResponse<AuthResponse>>

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<ApiResponse<AuthResponse>>

    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") token: String
    ): Response<ApiResponse<Unit>>

    @GET("auth/me")
    suspend fun getMe(
        @Header("Authorization") token: String
    ): Response<ApiResponse<UserData>>

    @POST("dispositivo/registro")
    suspend fun registerDevice(
        @Body request: DeviceRegistrationRequest
    ): Response<ApiResponse<DeviceData>>

    @POST("dispositivo/desactivar")
    suspend fun deactivateDevice(
        @Body request: Map<String, String>
    ): Response<ApiResponse<Unit>>

    @POST("compra/registrar")
    suspend fun registerPurchase(
        @Header("Authorization") token: String,
        @Body request: PurchaseRegistrationRequest
    ): Response<ApiResponse<PurchaseData>>

    @GET("compra/verificar")
    suspend fun verifyPremiumStatus(
        @Header("Authorization") token: String
    ): Response<ApiResponse<PurchaseStatusResponse>>

    @POST("compra/invalidar")
    suspend fun invalidatePurchase(
        @Header("Authorization") token: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<PurchaseData>>

    @GET("configuracion")
    suspend fun getConfiguracion(
        @Header("Authorization") token: String
    ): Response<ApiResponse<ConfiguracionResponse>>

    @POST("configuracion")
    suspend fun updateConfiguracion(
        @Header("Authorization") token: String,
        @Body request: ConfiguracionUpdateRequest
    ): Response<ApiResponse<ConfiguracionResponse>>
}
