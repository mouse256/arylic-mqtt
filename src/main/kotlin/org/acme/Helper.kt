@file:OptIn(ExperimentalUnsignedTypes::class)

package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging

class Helper {
    @OptIn(ExperimentalUnsignedTypes::class)
    companion object {
        private val log = KotlinLogging.logger {}
        private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()

        fun littleEndianInt(data: UByteArray): UInt? {

            if (data.size < 4) {
                return null
            }
            val res =
                data[0].toUInt() +
                (data[1].toUInt() shl 8) +
                (data[2 ].toUInt() shl 16) +
                (data[ 3 ].toUInt() shl 24)
            log.debug { "littleEndianInt: ${bytesToHex(data)}  -> $res" }
            return res
        }

        fun littleEndianEncode(input: UInt): UByteArray {
            val data = UByteArray(4)
            data[3] = (input shr 24).toUByte()
            data[2] = ((input shl 8) shr 24).toUByte()
            data[1] = ((input shl 16) shr 24).toUByte()
            data[0] = ((input shl 24) shr 24).toUByte()
            log.debug { "littleEndianEncode: $input -> ${bytesToHex(data)}" }
            return data
        }

        fun bytesToHex(bytes: ByteArray): String {
            val hexChars = CharArray(bytes.size * 3)
            for (j in bytes.indices) {
                val v: Int = bytes[j].toInt() and 0xFF
                hexChars[j * 3] = HEX_ARRAY[v ushr 4]
                hexChars[j * 3 + 1] = HEX_ARRAY[v and 0x0F]
                hexChars[j * 3 + 2] = ' '
            }
            return String(hexChars)
        }

        fun bytesToHex(bytes: UByteArray): String {
            val hexChars = CharArray(bytes.size * 3)
            for (j in bytes.indices) {
                val v: Int = bytes[j].toInt() and 0xFF
                hexChars[j * 3] = HEX_ARRAY[v ushr 4]
                hexChars[j * 3 + 1] = HEX_ARRAY[v and 0x0F]
                hexChars[j * 3 + 2] = ' '
            }
            return String(hexChars)
        }
    }

}