package app.luzzy.interfaces

import androidx.room.Dao
import androidx.room.Query
import app.luzzy.models.MessageAttachment

@Dao
interface MessageAttachmentsDao {
    @Query("SELECT * FROM message_attachments")
    fun getAll(): List<MessageAttachment>
}
