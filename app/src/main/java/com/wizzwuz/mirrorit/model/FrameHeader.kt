package com.wizzwuz.mirrorit.model

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

data class FrameHeader(
    val type: Byte,
    val timestampUs: Long,
    val payloadLength: Int
) {
    companion object {
        const val MAGIC_BYTE_1: Byte = 0x4D // 'M'
        const val MAGIC_BYTE_2: Byte = 0x49 // 'I'

        const val TYPE_CONFIG: Byte = 0x01
        const val TYPE_KEY_FRAME: Byte = 0x02
        const val TYPE_DELTA_FRAME: Byte = 0x03
        const val TYPE_HEARTBEAT: Byte = 0x04

        fun write(out: DataOutputStream, type: Byte, timestampUs: Long, length: Int) {
            out.writeByte(MAGIC_BYTE_1.toInt())
            out.writeByte(MAGIC_BYTE_2.toInt())
            out.writeByte(type.toInt())
            out.writeLong(timestampUs)
            out.writeInt(length)
        }

        fun read(input: DataInputStream): FrameHeader? {
            try {
                // Seek for MAGIC_BYTE_1 to allow stream resynchronization
                var b1 = input.readByte()
                while (b1 != MAGIC_BYTE_1) {
                    b1 = input.readByte()
                }
                val b2 = input.readByte()
                if (b2 != MAGIC_BYTE_2) {
                    return null
                }
                val type = input.readByte()
                val timestampUs = input.readLong()
                val length = input.readInt()
                return FrameHeader(type, timestampUs, length)
            } catch (_: EOFException) {
                return null
            } catch (_: Exception) {
                return null
            }
        }
    }
}
