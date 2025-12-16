package com.goodwy.smsmessenger.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.goodwy.smsmessenger.models.ContactSendMode
import com.goodwy.smsmessenger.models.SendMode

@Dao
interface ContactSendModeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(contactSendMode: ContactSendMode)

    @Query("SELECT * FROM contact_send_modes WHERE thread_id = :threadId")
    fun getSendMode(threadId: Long): ContactSendMode?

    @Query("SELECT send_mode FROM contact_send_modes WHERE thread_id = :threadId")
    fun getSendModeValue(threadId: Long): SendMode?

    @Query("DELETE FROM contact_send_modes WHERE thread_id = :threadId")
    fun delete(threadId: Long)

    @Query("SELECT * FROM contact_send_modes")
    fun getAll(): List<ContactSendMode>
}
