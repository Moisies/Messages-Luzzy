package app.luzzy.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goodwy.commons.helpers.ensureBackgroundThread

abstract class SendStatusReceiver : BroadcastReceiver() {

    abstract fun updateAndroidDatabase(context: Context, intent: Intent, receiverResultCode: Int)

    abstract fun updateAppDatabase(context: Context, intent: Intent, receiverResultCode: Int)

    override fun onReceive(context: Context, intent: Intent) {
        val resultCode = resultCode
        ensureBackgroundThread {
            updateAndroidDatabase(context, intent, resultCode)
            updateAppDatabase(context, intent, resultCode)
        }
    }

    companion object {
        const val SMS_SENT_ACTION = "app.luzzy.Receiver.SMS_SENT"
        const val SMS_DELIVERED_ACTION = "app.luzzy.Receiver.SMS_DELIVERED"

        const val EXTRA_ERROR_CODE = "errorCode"
        const val EXTRA_SUB_ID = "subId"

        const val NO_ERROR_CODE = -1
    }
}
