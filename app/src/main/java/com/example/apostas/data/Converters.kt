package com.example.apostas.data

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromStatus(status: StatusAposta): String {
        return status.name
    }

    @TypeConverter
    fun toStatus(value: String): StatusAposta {
        return StatusAposta.valueOf(value)
    }
}
