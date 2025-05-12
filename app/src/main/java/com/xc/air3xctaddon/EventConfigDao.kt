package com.xc.air3xctaddon

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventConfigDao {
    @Query("SELECT * FROM event_configs ORDER BY position")
    fun getAllConfigs(): Flow<List<EventConfig>>

    @Query("SELECT * FROM event_configs ORDER BY position")
    suspend fun getAllConfigsSync(): List<EventConfig>

    @Insert
    suspend fun insert(config: EventConfig)

    @Update
    suspend fun update(config: EventConfig)

    @Delete
    suspend fun delete(config: EventConfig)

    @Query("UPDATE event_configs SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Int, position: Int)
}