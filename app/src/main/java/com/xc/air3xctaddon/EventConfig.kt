package com.xc.air3xctaddon

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_configs")
data class EventConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val event: String, // Changed from Event to String to support all XCTrack events
    val soundFile: String,
    val volumeType: VolumeType,
    val volumePercentage: Int, // Used if volumeType is PERCENTAGE
    val playCount: Int,
    val position: Int // For ordering
)