package com.xc.air3xctaddon

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_configs")
data class EventConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val event: String,
    val taskType: String? = null, // e.g., "Sound", "SendPosition", "SendTelegramPosition"
    val taskData: String? = null, // e.g., sound file name or position data
    val volumeType: VolumeType? = null, // Nullable for non-Sound tasks
    val volumePercentage: Int? = null, // Nullable for non-Sound tasks
    val playCount: Int? = null, // Nullable for non-Sound tasks
    val position: Int? = null, // Nullable to align with usage
    val telegramChatId: String? = null // Added for SendTelegramPosition tasks
)