package app.luzzy.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goodwy.commons.extensions.notificationManager
import com.goodwy.commons.helpers.ensureBackgroundThread
import app.luzzy.extensions.conversationsDB
import app.luzzy.extensions.deleteMessage
import app.luzzy.extensions.markThreadMessagesRead
import app.luzzy.extensions.updateLastConversationMessage
import app.luzzy.helpers.IS_MMS
import app.luzzy.helpers.MESSAGE_ID
import app.luzzy.helpers.THREAD_ID
import app.luzzy.helpers.refreshConversations
import app.luzzy.helpers.refreshMessages

class DeleteSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val messageId = intent.getLongExtra(MESSAGE_ID, 0L)
        val isMms = intent.getBooleanExtra(IS_MMS, false)
        context.notificationManager.cancel(threadId.hashCode())
        ensureBackgroundThread {
            context.markThreadMessagesRead(threadId)
            context.conversationsDB.markRead(threadId)
            context.deleteMessage(messageId, isMms)
            context.updateLastConversationMessage(threadId)
            refreshMessages()
            refreshConversations()
        }
    }
}
