package app.luzzy.interfaces

import androidx.room.Dao
import androidx.room.Query
import app.luzzy.models.Attachment

@Dao
interface AttachmentsDao {
    @Query("SELECT * FROM attachments")
    fun getAll(): List<Attachment>
}
