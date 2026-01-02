package app.luzzy.network.models

data class RegisterDeviceRequest(
    val phone: String,
    val registrationToken: String
)

data class RegisterDeviceResponse(
    val token: String
)

data class Message(
    val from: String,
    val message: String,
    val timestamp: String
)

data class SendMessagesRequest(
    val from: String,
    val to: String,
    val messages: List<Message>
)
