package com.xc.air3xctaddon.converters

import androidx.room.TypeConverter
import com.xc.air3xctaddon.VolumeType

class Converters {
    @TypeConverter
    fun fromVolumeType(value: VolumeType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toVolumeType(value: String?): VolumeType? {
        return value?.let { VolumeType.valueOf(it) }
    }
}