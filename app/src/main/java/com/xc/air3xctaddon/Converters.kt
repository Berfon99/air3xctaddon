package com.xc.air3xctaddon.converters

import androidx.room.TypeConverter
import com.xc.air3xctaddon.Event
import com.xc.air3xctaddon.VolumeType

class Converters {
    @TypeConverter
    fun fromEvent(event: Event): String {
        return event.name
    }

    @TypeConverter
    fun toEvent(name: String): Event {
        return Event.valueOf(name)
    }

    @TypeConverter
    fun fromVolumeType(volumeType: VolumeType): String {
        return volumeType.name
    }

    @TypeConverter
    fun toVolumeType(name: String): VolumeType {
        return VolumeType.valueOf(name)
    }
}