package com.xc.air3xctaddon

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.xc.air3xctaddon.converters.Converters

@Database(entities = [EventConfig::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventConfigDao(): EventConfigDao
}