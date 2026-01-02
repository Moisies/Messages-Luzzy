package app.luzzy.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import app.luzzy.R
import app.luzzy.activities.ThreadActivity
import com.goodwy.commons.helpers.ensureBackgroundThread
import app.luzzy.extensions.getThreadId
import app.luzzy.extensions.saveSmsDraft

class DraftManager(private val context: Context) {

    companion object {
        private const val TAG = "DraftManager"
        private const val CHANNEL_ID = "fcm_draft_messages"
        private const val NOTIFICATION_ID_BASE = 2000
    }

    fun saveDraftAndNotify(recipient: String, message: String) {
        ensureBackgroundThread {
            try {
                val threadId = context.getThreadId(recipient)

                if (threadId == 0L) {
                    Log.e(TAG, "No se pudo obtener threadId para $recipient")
                    return@ensureBackgroundThread
                }

                context.saveSmsDraft(message, threadId)

                showDraftNotification(recipient, message, threadId)

                Log.d(TAG, "Borrador guardado y notificación mostrada para $recipient")
            } catch (e: Exception) {
                Log.e(TAG, "Error al guardar borrador", e)
            }
        }
    }

    private fun showDraftNotification(recipient: String, message: String, threadId: Long) {
        createNotificationChannel()

        val intent = Intent(context, ThreadActivity::class.java).apply {
            putExtra("threadId", threadId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            threadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val messagePreview = if (message.length > 50) {
            message.substring(0, 50) + "..."
        } else {
            message
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_messages)
            .setContentTitle(context.getString(R.string.draft_message_ready_title, recipient))
            .setContentText(messagePreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_BASE + threadId.toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.draft_message_channel_name)
            val descriptionText = context.getString(R.string.draft_message_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
