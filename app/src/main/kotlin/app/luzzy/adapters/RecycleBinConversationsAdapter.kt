package app.luzzy.adapters

import android.view.Menu
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.extensions.isRTLLayout
import com.goodwy.commons.extensions.notificationManager
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.views.MyRecyclerView
import app.luzzy.R
import app.luzzy.activities.SimpleActivity
import app.luzzy.extensions.config
import app.luzzy.extensions.createTemporaryThread
import app.luzzy.extensions.deleteConversation
import app.luzzy.extensions.deleteMessage
import app.luzzy.extensions.deleteScheduledMessage
import app.luzzy.extensions.messagesDB
import app.luzzy.extensions.restoreAllMessagesFromRecycleBinForConversation
import app.luzzy.extensions.updateLastConversationMessage
import app.luzzy.extensions.updateScheduledMessagesThreadId
import app.luzzy.helpers.SWIPE_ACTION_DELETE
import app.luzzy.helpers.SWIPE_ACTION_RESTORE
import app.luzzy.helpers.generateRandomId
import app.luzzy.helpers.refreshConversations
import app.luzzy.messaging.cancelScheduleSendPendingIntent
import app.luzzy.models.Conversation
import app.luzzy.models.Message
import kotlin.collections.forEach

class RecycleBinConversationsAdapter(
    activity: SimpleActivity, recyclerView: MyRecyclerView, onRefresh: () -> Unit, itemClick: (Any) -> Unit
) : BaseConversationsAdapter(activity, recyclerView, onRefresh, itemClick, isRecycleBin = true) {
    override fun getActionMenuId() = R.menu.cab_recycle_bin_conversations

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_restore -> askConfirmRestore()
            R.id.cab_select_all -> selectAll()
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)

        val baseString = com.goodwy.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteConversations()
            }
        }
    }

    private fun deleteConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove = currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        conversationsToRemove.forEach {

            deleteMessages(it)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }

        removeConversationsFromList(conversationsToRemove)
    }

    private fun askConfirmRestore() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)

        val baseString = R.string.restore_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                restoreConversations()
            }
        }
    }

    private fun restoreConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove = currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        conversationsToRemove.forEach {
            activity.restoreAllMessagesFromRecycleBinForConversation(it.threadId)
        }

        removeConversationsFromList(conversationsToRemove)
    }

    private fun removeConversationsFromList(removedConversations: List<Conversation>) {
        val newList = try {
            currentList.toMutableList().apply { removeAll(removedConversations) }
        } catch (_: Exception) {
            currentList.toMutableList()
        }

        activity.runOnUiThread {
            if (newList.none { selectedKeys.contains(it.hashCode()) }) {
                refreshConversations()
                finishActMode()
            } else {
                submitList(newList)
                if (newList.isEmpty()) {
                    refreshConversations()
                }
            }
        }
    }

    override fun swipedLeft(conversation: Conversation) {
        val swipeLeftAction = if (activity.isRTLLayout) SWIPE_ACTION_RESTORE else SWIPE_ACTION_DELETE
        swipeAction(swipeLeftAction, conversation)
    }

    override fun swipedRight(conversation: Conversation) {
        val swipeRightAction = if (activity.isRTLLayout) SWIPE_ACTION_DELETE else SWIPE_ACTION_RESTORE
        swipeAction(swipeRightAction, conversation)
    }

    private fun swipeAction(swipeAction: Int, conversation: Conversation) {
        when (swipeAction) {
            SWIPE_ACTION_DELETE -> swipedDelete(conversation)
            else -> swipedRestore(conversation)
        }
    }

    private fun swipedDelete(conversation: Conversation) {
        val item = conversation.title
        val baseString = com.goodwy.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), item)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                swipedDeleteConversations(conversation)
            }
        }
    }

    private fun swipedDeleteConversations(conversation: Conversation) {

        deleteMessages(conversation)
        activity.notificationManager.cancel(conversation.threadId.hashCode())

        val conversationsToRemove = ArrayList<Conversation>()
        conversationsToRemove.add(conversation)
        removeConversationsFromList(conversationsToRemove)
    }

    private fun deleteMessages(
        conversation: Conversation,
    ) {
        val threadId = conversation.threadId
        val messagesToRemove = try {

            activity.messagesDB.getThreadMessagesFromRecycleBin(threadId).toMutableList() as ArrayList<Message>
        } catch (_: Exception) {
            ArrayList()
        }

        messagesToRemove.forEach { message ->
            val messageId = message.id
            if (message.isScheduled) {
                activity.deleteScheduledMessage(messageId)
                activity.cancelScheduleSendPendingIntent(messageId)
            } else {
                activity.deleteMessage(messageId, message.isMMS)
            }
        }

        if (messagesToRemove.isNotEmpty() && messagesToRemove.all { it.isScheduled }) {
            val scheduledMessage = messagesToRemove.last()
            val fakeThreadId = generateRandomId()
            activity.createTemporaryThread(scheduledMessage, fakeThreadId, conversation)
            activity.updateScheduledMessagesThreadId(messagesToRemove, fakeThreadId)
        }
    }

    private fun swipedRestore(conversation: Conversation) {
        val item = conversation.title
        val baseString = R.string.restore_confirmation
        val question = String.format(resources.getString(baseString), item)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                swipedRestoreConversations(conversation)
            }
        }
    }

    private fun swipedRestoreConversations(conversation: Conversation) {
        activity.restoreAllMessagesFromRecycleBinForConversation(conversation.threadId)

        val conversationsToRemove = ArrayList<Conversation>()
        conversationsToRemove.add(conversation)
        removeConversationsFromList(conversationsToRemove)
    }
}
