package app.luzzy.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goodwy.commons.extensions.copyToClipboard
import com.goodwy.commons.extensions.notificationManager
import com.goodwy.commons.helpers.ensureBackgroundThread
import app.luzzy.extensions.conversationsDB
import app.luzzy.extensions.deleteMessage
import app.luzzy.extensions.markThreadMessagesRead
import app.luzzy.extensions.updateLastConversationMessage
import app.luzzy.helpers.COPY_NUMBER
import app.luzzy.helpers.COPY_NUMBER_AND_DELETE
import app.luzzy.helpers.MESSAGE_ID
import app.luzzy.helpers.THREAD_ID
import app.luzzy.helpers.THREAD_TEXT
import app.luzzy.helpers.refreshConversations
import app.luzzy.helpers.refreshMessages

class CopyNumberReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            COPY_NUMBER -> {
                val body = intent.getStringExtra(THREAD_TEXT)
                val threadId = intent.getLongExtra(THREAD_ID, 0L)
                context.notificationManager.cancel(threadId.hashCode())
                ensureBackgroundThread {
                    context.copyToClipboard(body!!)
                    context.markThreadMessagesRead(threadId)
                    context.conversationsDB.markRead(threadId)
                    refreshMessages()
                    refreshConversations()
                }
            }
            COPY_NUMBER_AND_DELETE -> {
                val body = intent.getStringExtra(THREAD_TEXT)
                val threadId = intent.getLongExtra(THREAD_ID, 0L)
                val messageId = intent.getLongExtra(MESSAGE_ID, 0L)
                context.notificationManager.cancel(threadId.hashCode())
                ensureBackgroundThread {
                    context.copyToClipboard(body!!)
                    context.markThreadMessagesRead(threadId)
                    context.conversationsDB.markRead(threadId)
                    context.deleteMessage(messageId, false)
                    context.updateLastConversationMessage(threadId)
                    refreshMessages()
                    refreshConversations()
                }
            }
        }
    }
}
