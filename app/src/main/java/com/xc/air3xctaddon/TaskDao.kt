package com.xc.air3xctaddon

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task)

    @Query("SELECT * FROM tasks")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksSync(): List<Task>

    @Query("DELETE FROM tasks WHERE taskType = :taskType")
    suspend fun deleteAll(taskType: String)

    @Delete
    suspend fun delete(task: Task)
}