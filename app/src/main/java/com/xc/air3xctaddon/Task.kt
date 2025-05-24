package com.xc.air3xctaddon

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val taskType: String,
    val taskData: String,
    val taskName: String,
    val launchInBackground: Boolean
)