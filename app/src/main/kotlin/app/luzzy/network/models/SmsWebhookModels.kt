package app.luzzy.network.models

data class SmsInboundRequest(
    val receivedSms: SmsMessage,
    val history: List<SmsMessage>
)

data class SmsMessage(
    val from: String,
    val to: String,
    val body: String,
    val timestamp: Long,
    val type: String
)
