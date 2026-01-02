package app.luzzy.messaging

import android.content.Context
import android.telephony.SmsMessage
import android.util.Patterns
import android.widget.Toast.LENGTH_LONG
import com.klinker.android.send_message.Settings
import com.goodwy.commons.extensions.showErrorToast
import com.goodwy.commons.extensions.toast
import com.goodwy.commons.helpers.ensureBackgroundThread
import app.luzzy.R
import app.luzzy.extensions.config
import app.luzzy.extensions.getThreadId
import app.luzzy.extensions.messagingUtils
import app.luzzy.extensions.shortcutHelper
import app.luzzy.messaging.SmsException.Companion.EMPTY_DESTINATION_ADDRESS
import app.luzzy.messaging.SmsException.Companion.ERROR_PERSISTING_MESSAGE
import app.luzzy.messaging.SmsException.Companion.ERROR_SENDING_MESSAGE
import app.luzzy.models.Attachment

@Deprecated("TODO: Move/rewrite messaging config code into the app.")
fun Context.getSendMessageSettings(): Settings {
    val settings = Settings()
    settings.useSystemSending = true
    settings.deliveryReports = config.enableDeliveryReports
    settings.sendLongAsMms = config.sendLongMessageMMS
    settings.sendLongAsMmsAfter = 1
    settings.group = config.sendGroupMessageMMS
    return settings
}

fun Context.isLongMmsMessage(text: String, settings: Settings = getSendMessageSettings()): Boolean {
    val data = SmsMessage.calculateLength(text, false)
    val numPages = data.first()
    return numPages > settings.sendLongAsMmsAfter && settings.sendLongAsMms
}

fun Context.sendMessageCompat(
    text: String,
    addresses: List<String>,
    subId: Int?,
    attachments: List<Attachment>,
    messageId: Long? = null
) {
    val settings = getSendMessageSettings()
    if (subId != null) {
        settings.subscriptionId = subId
    }

    val messagingUtils = messagingUtils
    val isMms = attachments.isNotEmpty() || isLongMmsMessage(text, settings)
            || addresses.size > 1 && settings.group
    if (isMms) {

        if (attachments.isNotEmpty()) {
            val lastIndex = attachments.lastIndex
            if (attachments.size > 1) {
                for (i in 0 until lastIndex) {
                    val attachment = attachments[i]
                    messagingUtils.sendMmsMessage("", addresses, attachment, settings, messageId)
                }
            }

            val lastAttachment = attachments[lastIndex]
            messagingUtils.sendMmsMessage(text, addresses, lastAttachment, settings, messageId)
        } else {
            messagingUtils.sendMmsMessage(text, addresses, null, settings, messageId)
        }
    } else {
        try {
            messagingUtils.sendSmsMessage(
                text = text,
                addresses = addresses.toSet(),
                subId = settings.subscriptionId,
                requireDeliveryReport = settings.deliveryReports,
                messageId = messageId
            )
        } catch (e: SmsException) {
            when (e.errorCode) {
                EMPTY_DESTINATION_ADDRESS -> toast(
                    id = R.string.empty_destination_address,
                    length = LENGTH_LONG
                )

                ERROR_PERSISTING_MESSAGE -> toast(
                    id = R.string.unable_to_save_message,
                    length = LENGTH_LONG
                )

                ERROR_SENDING_MESSAGE -> toast(
                    msg = getString(R.string.unknown_error_occurred_sending_message, e.errorCode),
                    length = LENGTH_LONG
                )
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
    ensureBackgroundThread {
        val threadId = getThreadId(addresses.toSet())
        shortcutHelper.reportSendMessageUsage(threadId)
    }
}

fun isShortCodeWithLetters(address: String): Boolean {
    if (Patterns.EMAIL_ADDRESS.matcher(address).matches()) {

        return false
    }

    return address.any { it.isLetter() }
}
