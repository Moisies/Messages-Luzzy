package app.luzzy.network.models

data class GoogleLoginRequest(
    val email: String,
    val deviceToken: String,
    val displayName: String? = null,
    val photoUrl: String? = null
)

data class GoogleLoginResponse(
    val token: String,
    val user: GoogleUserData
)

data class GoogleUserData(
    val email: String,
    val displayName: String?,
    val photoUrl: String?
)
