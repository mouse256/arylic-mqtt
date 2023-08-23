package org.acme

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.lang.IllegalStateException

@OptIn(ExperimentalUnsignedTypes::class)
@ApplicationScoped
class ArylicSerde {
    companion object {
        private val log = KotlinLogging.logger {}
        private val lengthSize = 4
        private val checksumSize = 4
        private val HEADER = ubyteArrayOf(0x18u, 0x96u, 0x18u, 0x20u)
        private val reservedSize = 8

        private val RESERVED = ubyteArrayOf(0x0u, 0x0u, 0x0u, 0x0u, 0x0u, 0x0u, 0x0u, 0x0u)
        val AXX = toByteArray("AXX")
        val MCU = toByteArray("MCU")
        val VOL = toByteArray("VOL")
        val MUT = toByteArray("MUT")
        val DAT = toByteArray("DAT")
        val MUT_MUTE = toByteArray("001")
        val MUT_UNMUTE = toByteArray("000")
        val PLUS = '+'.code.toUByte()
        val LF = '\n'.code.toUByte()
        val MEA = toByteArray("MEA")
        val PLY = toByteArray("PLY")
        val DEV = toByteArray("DEV")
        val GET = toByteArray("GET")
        val RDY = toByteArray("RDY")
        val INF = toByteArray("INF")
        val PINFGET = toByteArray("PINFGET")
        private val UNKNOWN = toByteArray("UNKNOWN")

        private fun toByteArray(data: String): UByteArray {
            return data.toCharArray().map { it.code.toUByte() }.toUByteArray()
        }
    }

    @Inject
    lateinit var objectMapper: ObjectMapper

    fun encode(msg: SentCommand): UByteArray {
        val payload = msg.toPayload()
        val checksum = Helper.littleEndianEncode(payload.sumOf { it.toUInt() })
        val length = Helper.littleEndianEncode(payload.size.toUInt())
        val data = HEADER + length + checksum + RESERVED + payload
        log.debug { "encoded payload: " + Helper.bytesToHex(data) }
        return data
    }

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


    fun decode(data: UData, handler: (ReceiveCommand) -> Unit) {
        log.debug { "Decoding" }

        if (!decodeHeader(data)) {
            log.warn { "Can't parse header" }
        }

        val length = decodeSize(data)
        if (length == null) {
            log.warn { "Can't parse length" }
            return
        }
        log.debug { "parsed length: $length" }

        val checksum = decodeChecksum(data)
        if (checksum == null) {
            log.warn { "Can't parse checksum" }
            return
        }
        log.debug { "checksum: $checksum" }
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
        log.debug { "payload: " + String(payload.toByteArray()) }
        decodePayload(UData(payload), handler)
    }

    private fun expectPlus(payload: UData, pos: Int) {
        if (PLUS != payload.next(1)?.get(0)) {
            log.warn { "Expected + at position $pos: ${payload.asString()}" }
            return
        }
    }

    private fun decodePayload(payload: UData, handler: (ReceiveCommand) -> Unit) {
        val id1 = payload.next(3)
        if (id1 == null) {
            log.warn { "Payload too small: ${payload.asString()}" }
            return
        }
        expectPlus(payload, 4)
        when {
            id1.contentEquals(AXX) -> {
                log.debug { "AXX!" }
                if (payload.remaining() == UNKNOWN.size && payload.fetch(UNKNOWN.size).contentEquals(UNKNOWN)) {
                    log.warn { "\"UNKNOWN\" message received, ignoring" }
                    return
                }
                val id2 = payload.next(3)
                if (id2 == null) {
                    log.warn { "Payload too small: ${payload.asString()}" }
                    return
                }
                expectPlus(payload, 8)
                when {
                    id2.contentEquals(VOL) -> {
                        log.info { "VOL" }
                        return
                    }

                    id2.contentEquals(MUT) -> handleMut(payload, handler)
                    id2.contentEquals(MEA) -> handleMea(payload, handler)
                    id2.contentEquals(PLY) -> handlePly(payload, handler)

                    else -> {
                        log.warn { "Unknown id2: ${payload.asString()}" }
                        return
                    }
                }
            }

            id1.contentEquals(MCU) -> log.info { "MCU!" }
            else -> log.warn { "Can't parse payload id1: ${payload.asString()}" }
        }
    }

    /**
     * AXX+MUT+NNN
     * System mute status. '000' for unmute, '001' for mute
     */
    private fun handleMut(payload: UData, handler: (ReceiveCommand) -> Unit) {
        val data = payload.next(3)
        when {
            MUT_MUTE.contentEquals(data) -> {
                log.info { "Received command: Mute enabled" }
                handler.invoke(Command.Mute(true))
            }

            MUT_UNMUTE.contentEquals(data) -> {
                log.info { "Received command: Mute disabled" }
                handler.invoke(Command.Mute(false))
            }

            else -> log.warn { "Can't parse MUT command: $payload" }
        }
    }

    private fun handleMea(payload: UData, handler: (ReceiveCommand) -> Unit) {
        val dat = payload.next(3)
        if (DAT.contentEquals(dat)) {
            handleData(payload, handler)
        }
        else if (RDY.contentEquals(dat)) {
            handleRdy(handler)
            return
        } else {
            log.warn { "Unknown MEA command, got ${payload.asString()}" }
        }
    }

    private fun handlePly(payload: UData, handler: (ReceiveCommand) -> Unit) {
        val dat = payload.next(3)
        if (INF.contentEquals(dat)) {
            handleInf(payload, handler)
        } else {
            log.warn { "Unknown PLY command, got ${payload.asString()}" }
        }
    }

    /**
     * AXX+MEA+DATdata&
     * data: json format , {"title":"HEXSTRING", "artist ":"HEXSTRING",
     * "album":"HEXSTRING","vendor":"HEXSTRING"}
     * eg: AXX+MEA+DAT { "title": "E88081E78BBC202D20E5908CE6A18CE79A84E4BDA0",
     * "artist ": "", "album": "", "vendor": "" }&
     */
    private fun handleData(payload: UData, handler: (ReceiveCommand) -> Unit) {
        val data = payload.next(payload.remaining() -2) //ends with "&" and "\n"
        log.debug { "DATA: ${String(data!!.asByteArray())}" }
        val dataJsonHex = objectMapper.readValue<Command.Data>(data!!.asByteArray())
        val dataJson = Command.Data(
            title = hexToString(dataJsonHex.title),
            artist = hexToString(dataJsonHex.artist),
            album = hexToString(dataJsonHex.album),
            vendor = hexToString(dataJsonHex.vendor),
            skipLimit = dataJsonHex.skipLimit
        )
        log.info { "Received $dataJson" }
        handler.invoke(dataJson)
    }

    /**
     * AXX+PLY+INFdata&
     * data: json format
     */
    private fun handleInf(payload: UData, handler: (ReceiveCommand) -> Unit) {
        val data = payload.next(payload.remaining() -2) //ends with "&" and "\n"
        log.debug { "Inf: ${String(data!!.asByteArray())}" }
        val dataJsonHex = objectMapper.readValue<Command.Inf>(data!!.asByteArray())

        log.info { "Received $dataJsonHex" }
        handler.invoke(dataJsonHex)
    }

    /**
     * AXX+MEA+RDY
     * Ready command?
     */
    private fun handleRdy(handler: (ReceiveCommand) -> Unit) {
        log.info { "Received READY" }
        handler.invoke(Command.Ready)
    }

    private fun hexToString(input: String): String {
        return String(input.chunked(2)
            .map { it.toUByte(16) }
            .toUByteArray()
            .toByteArray())
    }

}