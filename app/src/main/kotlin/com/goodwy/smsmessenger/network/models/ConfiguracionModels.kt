package com.goodwy.smsmessenger.network.models

data class ConfiguracionData(
    val nombre_usuario: String?,
    val mensaje_automatico: String?,
    val firma_sms: String?,
    val tema_color: String?,
    val auto_respuesta_activada: Boolean,
    val notificaciones_silenciosas: Boolean,
    val modo_oscuro: Boolean
)

data class ConfiguracionResponse(
    val configuracion: ConfiguracionData,
    val is_premium: Boolean
)

data class ConfiguracionUpdateRequest(
    val nombre_usuario: String?,
    val mensaje_automatico: String?,
    val firma_sms: String?,
    val tema_color: String?,
    val auto_respuesta_activada: Boolean?,
    val notificaciones_silenciosas: Boolean?,
    val modo_oscuro: Boolean?
)
