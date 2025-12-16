package com.goodwy.smsmessenger.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_send_modes")
data class ContactSendMode(
    @PrimaryKey
    @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "send_mode") val sendMode: SendMode = SendMode.SEND
)

enum class SendMode {
    SEND,
    DRAFT
}
