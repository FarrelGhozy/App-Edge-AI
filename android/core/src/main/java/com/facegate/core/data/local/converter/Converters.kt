package com.facegate.core.data.local.converter

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Converters {
    @TypeConverter
    fun floatArrayToByteArray(value: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(value.size * 4).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            value.forEach { putFloat(it) }
        }
        return buffer.array()
    }

    @TypeConverter
    fun byteArrayToFloatArray(value: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(value).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }
        val result = FloatArray(value.size / 4)
        for (i in result.indices) {
            result[i] = buffer.getFloat()
        }
        return result
    }
}
