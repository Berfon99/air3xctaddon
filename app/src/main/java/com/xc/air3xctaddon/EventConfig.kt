package com.xc.air3xctaddon

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_configs")
data class EventConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val event: String,
    val taskType: String?,
    val taskData: String?,
    val volumeType: VolumeType,
    val volumePercentage: Int,
    val playCount: Int,
    val position: Int,
    val telegramChatId: String?,
    val telegramGroupName: String?,
    val launchInBackground: Boolean = true
)