package com.wizzwuz.mirrorit.model

import java.io.DataInputStream
import java.io.DataOutputStream

sealed class ControlPacket {

    data class Touch(
        val action: Int,          // 0: DOWN, 1: MOVE, 2: UP, 3: CANCEL
        val pointerId: Int,
        val normalizedX: Float,   // 0.0f to 1.0f
        val normalizedY: Float    // 0.0f to 1.0f
    ) : ControlPacket() {
        companion object {
            const val ACTION_DOWN = 0
            const val ACTION_MOVE = 1
            const val ACTION_UP = 2
            const val ACTION_CANCEL = 3
        }
    }

    data class Key(
        val keyCode: Int
    ) : ControlPacket() {
        companion object {
            const val KEY_BACK = 4
            const val KEY_HOME = 3
            const val KEY_RECENTS = 187
            const val KEY_POWER = 26
            const val KEY_VOLUME_UP = 24
            const val KEY_VOLUME_DOWN = 25
        }
    }

    data class Text(
        val content: String
    ) : ControlPacket()

    companion object {
        const val TYPE_TOUCH: Byte = 0x10
        const val TYPE_KEY: Byte = 0x11
        const val TYPE_TEXT: Byte = 0x12

        fun write(out: DataOutputStream, packet: ControlPacket) {
            when (packet) {
                is Touch -> {
                    out.writeByte(TYPE_TOUCH.toInt())
                    out.writeInt(packet.action)
                    out.writeInt(packet.pointerId)
                    out.writeFloat(packet.normalizedX)
                    out.writeFloat(packet.normalizedY)
                }
                is Key -> {
                    out.writeByte(TYPE_KEY.toInt())
                    out.writeInt(packet.keyCode)
                }
                is Text -> {
                    out.writeByte(TYPE_TEXT.toInt())
                    val bytes = packet.content.toByteArray(Charsets.UTF_8)
                    out.writeInt(bytes.size)
                    out.write(bytes)
                }
            }
            out.flush()
        }

        fun read(input: DataInputStream): ControlPacket? {
            val type = input.readByte()
            return when (type) {
                TYPE_TOUCH -> {
                    val action = input.readInt()
                    val pointerId = input.readInt()
                    val normX = input.readFloat()
                    val normY = input.readFloat()
                    Touch(action, pointerId, normX, normY)
                }
                TYPE_KEY -> {
                    val keyCode = input.readInt()
                    Key(keyCode)
                }
                TYPE_TEXT -> {
                    val length = input.readInt()
                    val bytes = ByteArray(length)
                    input.readFully(bytes)
                    Text(String(bytes, Charsets.UTF_8))
                }
                else -> null
            }
        }
    }
}
