package com.goodwy.smsmessenger.helpers

import android.content.Context
import com.goodwy.smsmessenger.databases.MessagesDatabase
import com.goodwy.smsmessenger.models.ContactSendMode
import com.goodwy.smsmessenger.models.SendMode

class ContactSendModeRepository(context: Context) {

    private val dao = MessagesDatabase.getInstance(context).ContactSendModeDao()

    fun getSendMode(threadId: Long): SendMode {
        return dao.getSendModeValue(threadId) ?: SendMode.SEND
    }

    fun setSendMode(threadId: Long, sendMode: SendMode) {
        dao.insertOrUpdate(ContactSendMode(threadId, sendMode))
    }

    fun toggleSendMode(threadId: Long): SendMode {
        val current = getSendMode(threadId)
        val newMode = if (current == SendMode.SEND) SendMode.DRAFT else SendMode.SEND
        setSendMode(threadId, newMode)
        return newMode
    }

    fun resetToSend(threadId: Long) {
        dao.delete(threadId)
    }

    fun getAllContactModes(): List<ContactSendMode> {
        return dao.getAll()
    }
}
