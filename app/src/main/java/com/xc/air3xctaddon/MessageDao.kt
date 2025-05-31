package com.xc.air3xctaddon

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: Message): Long

    @Query("SELECT * FROM messages ORDER BY title ASC")
    fun getAllMessages(): Flow<List<Message>>

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteById(messageId: Long)
}