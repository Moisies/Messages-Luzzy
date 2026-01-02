package app.luzzy.receivers

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.bumptech.glide.Glide
import com.klinker.android.send_message.MmsReceivedReceiver
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import app.luzzy.R
import app.luzzy.extensions.config
import app.luzzy.extensions.getConversations
import app.luzzy.extensions.getLatestMMS
import app.luzzy.extensions.insertOrUpdateConversation
import app.luzzy.extensions.notificationHelper
import app.luzzy.extensions.shouldUnarchive
import app.luzzy.extensions.showReceivedMessageNotification
import app.luzzy.extensions.updateConversationArchivedStatus
import app.luzzy.helpers.ReceiverUtils.isMessageFilteredOut
import app.luzzy.helpers.refreshConversations
import app.luzzy.helpers.refreshMessages
import app.luzzy.models.Message

class MmsReceiver : MmsReceivedReceiver() {

    override fun isAddressBlocked(context: Context, address: String): Boolean {
        val normalizedAddress = address.normalizePhoneNumber()
        return context.isNumberBlocked(normalizedAddress)
    }

    override fun isContentBlocked(context: Context, content: String): Boolean {
        return isMessageFilteredOut(context, content)
    }

    override fun onMessageReceived(context: Context, messageUri: Uri) {
        val mms = context.getLatestMMS() ?: return
        val address = mms.getSender()?.phoneNumbers?.first()?.normalizedNumber ?: ""

        val size = context.resources.getDimension(R.dimen.notification_large_icon_size).toInt()
        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            if (context.baseConfig.blockUnknownNumbers) {
                val simpleContactsHelper = SimpleContactsHelper(context)
                simpleContactsHelper.exists(address, privateCursor) { exists ->
                    if (exists) {
                        handleMmsMessage(context, mms, size, address)
                    }
                }
            } else {
                handleMmsMessage(context, mms, size, address)
            }
        }

        if (context.config.notifyTurnsOnScreen) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakelock = powerManager.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "goodwy.messages:mms.receiver"
            )
            wakelock.acquire(3000)
        }
    }

    override fun onError(context: Context, error: String) {
        context.notificationHelper.showMmsReceivedFailedNotification()

    }

    private fun handleMmsMessage(
        context: Context,
        mms: Message,
        size: Int,
        address: String
    ) {
        val glideBitmap = try {
            Glide.with(context)
                .asBitmap()
                .load(mms.attachment!!.attachments.first().getUri())
                .centerCrop()
                .into(size, size)
                .get()
        } catch (_: Exception) {
            null
        }

        Handler(Looper.getMainLooper()).post {
            context.showReceivedMessageNotification(
                messageId = mms.id,
                address = address,
                body = mms.body,
                threadId = mms.threadId,
                bitmap = glideBitmap,
                subscriptionId = mms.subscriptionId
            )
            ensureBackgroundThread {
                val conversation = context.getConversations(mms.threadId).firstOrNull()
                    ?: return@ensureBackgroundThread
                context.insertOrUpdateConversation(conversation)
                if (context.shouldUnarchive()) {
                    context.updateConversationArchivedStatus(mms.threadId, false)
                }
                refreshMessages()
                refreshConversations()
            }
        }
    }
}
