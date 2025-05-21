package com.xc.air3xctaddon

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_configs")
data class EventConfig(
    @PrimaryKey val id: Int,
    val event: String,
    val taskType: String? = null, // e.g., "Sound" or "SendPosition"
    val taskData: String? = null, // e.g., sound file name or position data
    val volumeType: VolumeType,
    val volumePercentage: Int,
    val playCount: Int,
    val position: Int
)