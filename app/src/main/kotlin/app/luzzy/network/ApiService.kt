package app.luzzy.network

import app.luzzy.network.models.GoogleLoginRequest
import app.luzzy.network.models.GoogleLoginResponse
import app.luzzy.network.models.RegisterDeviceRequest
import app.luzzy.network.models.RegisterDeviceResponse
import app.luzzy.network.models.SendMessagesRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    @POST("register")
    suspend fun registerDevice(
        @Body request: RegisterDeviceRequest
    ): Response<RegisterDeviceResponse>

    @POST("auth/google-login")
    suspend fun googleLogin(
        @Body request: GoogleLoginRequest
    ): Response<GoogleLoginResponse>

    @GET("settings")
    suspend fun getSettings(
        @Header("Authorization") token: String
    ): Response<Map<String, Any>>

    @POST("settings")
    suspend fun updateSettings(
        @Header("Authorization") token: String,
        @Body settings: Map<String, Any>
    ): Response<String>

    @POST("messages")
    suspend fun sendMessages(
        @Header("Authorization") token: String,
        @Body request: SendMessagesRequest
    ): Response<String>
}
