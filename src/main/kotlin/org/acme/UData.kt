package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging

@OptIn(ExperimentalUnsignedTypes::class)
class UData(private val data : UByteArray) {

    private val log = KotlinLogging.logger {}
    private var pos =0;
    constructor(dataSigned: ByteArray, size: Int):this(dataSigned.toUByteArray().copyOfRange(0, size))

    fun next(size: Int): UByteArray? {
        log.info { "Next: $pos" }
        if (data.size < pos + size) {
            return null
        }
        val newData = data.copyOfRange(pos, pos+size)
        pos += size
        return newData
    }

    fun remaining(): Int {
        return data.size - pos
    }

}