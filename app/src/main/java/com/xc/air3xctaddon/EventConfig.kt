package com.xc.air3xctaddon

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_configs")
data class EventConfig(
    @PrimaryKey val id: Int,
    val event: String,
    val taskType: String? = null,
    val taskData: String? = null,
    val volumeType: VolumeType,
    val volumePercentage: Int,
    val playCount: Int,
    val position: Int,
    val telegramChatId: String? = null,
    val telegramGroupName: String? = null
)