package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging

@OptIn(ExperimentalUnsignedTypes::class)
class ArylicSerde {

    private val log = KotlinLogging.logger {}
    private val lengthSize = 4
    private val checksumSize = 4
    private val HEADER = ubyteArrayOf(0x18u, 0x96u, 0x18u, 0x20u)
    private val reservedSize = 8



    private fun decodeHeader(data: UData): Boolean {
        data.next(HEADER.size)?.let {
            if (it.contentEquals(HEADER)) {
                return true
            }
        }
        return false
    }

    private fun decodeSize(data: UData): UInt? {
        data.next(lengthSize)?.let {
            return Helper.littleEndianInt(it)
        }
        return null
    }

    private fun decodeChecksum(data: UData): UInt? {
        data.next(checksumSize)?.let {
            return Helper.littleEndianInt(it)
        }
        return null
    }


    fun decode(data: UData) {
        log.info { "Decoding" }

        if (!decodeHeader(data)) {
            log.warn {"Can't parse header" }
        }

        val length = decodeSize(data)
        if (length == null) {
            log.warn { "Can't parse length" }
            return
        }
        log.info { "parsed length: $length" }

        val checksum = decodeChecksum(data)
        if (checksum == null) {
            log.warn { "Can't parse checksum" }
            return
        }
        log.info { "checksum: $checksum" }
        data.next(reservedSize) // 8 bytes reserved, skip them

        if (data.remaining().toUInt() != length) {
            log.warn { "Msg size wrong. Expected $length bytes, got ${data.remaining()}" }
            return
        }

        val payload = data.next(data.remaining())
        val sum = payload!!.sumOf { it.toUInt() }
        if (checksum != sum) {
            log.warn { "Checksum mismatch. Expected $checksum bytes, got $sum" }
            return
        }
        log.info { "payload: " + String(payload.toByteArray()) }
    }

    ///18 96 18 20 0C 00 00 00 D7 02 00 00 00 00 00 00 00 00 00 00 41 58 58 2B 4D 55 54 2B 30 30 30 0A
    //2023-08-05 12:50:05,391 INFO  [org.acm.ArylicConnection] (DefaultDispatcher-worker-2) IN2: �
    //                                                                                             �AXX+MUT+000

//    private fun formatMsgAndSend(data: ByteArray) {
//        val xx = HEADER + HEADER
//        val cmdArray = ByteArray(data.size + 4)
//
//        //first 3 bytes are to be filled in later
//        //val cmdArray = byteArrayOf(0, 0, 0, START_BYTE, cmd, 0, 0, data, STOP_BYTE)
//        cmdArray[0] = if (login) BYTE_LOGIN else BYTE_MSG
//        cmdArray[2] = (data.size and 0x000000FF).toByte()
//        cmdArray[1] = ((data.size shr 8) and 0x000000FF).toByte()
//        cmdArray[cmdArray.size - 1] = STOP_BYTE
//        data.copyInto(cmdArray, 3)
//        LOG.trace("s1: {}, s2: {}", cmdArray[1], cmdArray[2])
//
//        LOG.debug(
//            "Sending: {}{}", Common.bytesToHex(
//                PREFIX
//            ), Common.bytesToHex(cmdArray)
//        )
//        out.write(PREFIX)
//        out.write(cmdArray)
//        out.flush()
//    }
}