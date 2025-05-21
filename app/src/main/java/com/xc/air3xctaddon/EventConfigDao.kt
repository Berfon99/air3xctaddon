package com.xc.air3xctaddon

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import android.util.Log

@Dao
interface EventConfigDao {
    @Query("SELECT * FROM event_configs ORDER BY position")
    fun getAllConfigs(): Flow<List<EventConfig>>

    @Query("SELECT * FROM event_configs ORDER BY position")
    suspend fun getAllConfigsSync(): List<EventConfig> {
        return try {
            val configs = getAllConfigsSyncInternal()
            Log.d("EventConfigDao", "getAllConfigsSync retrieved ${configs.size} configs: ${configs.map { "${it.event} (${it.taskType}: ${it.taskData})" }}")
            configs
        } catch (e: Exception) {
            Log.e("EventConfigDao", "Error in getAllConfigsSync", e)
            emptyList()
        }
    }

    @Query("SELECT * FROM event_configs ORDER BY position")
    suspend fun getAllConfigsSyncInternal(): List<EventConfig>

    @Insert
    suspend fun insert(config: EventConfig) {
        Log.d("EventConfigDao", "Inserting config: event=${config.event}, taskType=${config.taskType}, taskData=${config.taskData}")
        insertInternal(config)
    }

    @Insert
    suspend fun insertInternal(config: EventConfig)

    @Update
    suspend fun update(config: EventConfig) {
        Log.d("EventConfigDao", "Updating config: id=${config.id}, event=${config.event}, taskType=${config.taskType}, taskData=${config.taskData}")
        updateInternal(config)
    }

    @Update
    suspend fun updateInternal(config: EventConfig)

    @Delete
    suspend fun delete(config: EventConfig) {
        Log.d("EventConfigDao", "Deleting config: id=${config.id}, event=${config.event}, taskType=${config.taskType}, taskData=${config.taskData}")
        deleteInternal(config)
    }

    @Delete
    suspend fun deleteInternal(config: EventConfig)

    @Query("UPDATE event_configs SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Int, position: Int) {
        Log.d("EventConfigDao", "Updating position: id=$id, position=$position")
        updatePositionInternal(id, position)
    }

    @Query("UPDATE event_configs SET position = :position WHERE id = :id")
    suspend fun updatePositionInternal(id: Int, position: Int)
}