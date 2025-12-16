package com.goodwy.smsmessenger.network.models

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val password_confirmation: String
)

data class UserData(
    val id: Int,
    val name: String,
    val email: String,
    val is_premium: Boolean
)

data class AuthResponse(
    val user: UserData,
    val token: String
)
