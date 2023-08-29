package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging

@OptIn(ExperimentalUnsignedTypes::class)
class UData(val data : UByteArray) {

    private var pos =0;
    private val log = KotlinLogging.logger {}
    constructor(dataSigned: ByteArray, size: Int):this(dataSigned.toUByteArray().copyOfRange(0, size))

    fun next(size: Int): UByteArray? {
        log.debug { "data.size: ${data.size} -- ${pos + size}" }
        if (data.size < pos + size) {
            return null
        }
        val newData = data.copyOfRange(pos, pos+size)
        pos += size
        return newData
    }

    fun remainder(): UData {
        return UData(fetch(remaining())!!)
    }

    fun fetch(size: Int): UByteArray? {
        if (data.size < pos + size) {
            return null
        }
        return data.copyOfRange(pos, pos+size)
    }

    fun remaining(): Int {
        return data.size - pos
    }

    fun asString(): String {
        return String(data.toByteArray())
    }

}