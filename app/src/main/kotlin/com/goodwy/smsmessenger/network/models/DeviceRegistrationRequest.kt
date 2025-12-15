package com.goodwy.smsmessenger.network.models

import com.google.gson.annotations.SerializedName

/**
 * Modelo de datos para el request de registro de dispositivo
 */
data class DeviceRegistrationRequest(
    @SerializedName("fcm_token")
    val fcmToken: String,

    @SerializedName("device_id")
    val deviceId: String? = null,

    @SerializedName("device_name")
    val deviceName: String? = null,

    @SerializedName("device_model")
    val deviceModel: String? = null,

    @SerializedName("os_version")
    val osVersion: String? = null,

    @SerializedName("app_version")
    val appVersion: String? = null
)

/**
 * Modelo de respuesta del servidor
 */
data class ApiResponse<T>(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String,

    @SerializedName("data")
    val data: T? = null,

    @SerializedName("errors")
    val errors: Map<String, List<String>>? = null
)

/**
 * Modelo de datos del dispositivo (respuesta)
 */
data class DeviceData(
    @SerializedName("id")
    val id: Int,

    @SerializedName("user_id")
    val userId: Int?,

    @SerializedName("fcm_token")
    val fcmToken: String,

    @SerializedName("device_id")
    val deviceId: String?,

    @SerializedName("device_name")
    val deviceName: String?,

    @SerializedName("device_model")
    val deviceModel: String?,

    @SerializedName("os_version")
    val osVersion: String?,

    @SerializedName("app_version")
    val appVersion: String?,

    @SerializedName("activo")
    val activo: Boolean,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String
)
