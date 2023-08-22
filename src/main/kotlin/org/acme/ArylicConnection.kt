package org.acme

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Socket
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.Promise
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * https://forum.arylic.com/t/latest-api-documents-and-uart-protocols/534/3
 * https://drive.google.com/file/d/1prKuVjpE0A9nSeNt_YiN5KeQOgkvTB6G/view
 */
@OptIn(ExperimentalUnsignedTypes::class)
class ArylicConnection(socket: Socket) {

    private val log = KotlinLogging.logger {}

    private val clientSocket = socket
    private val out: BufferedOutputStream = BufferedOutputStream(clientSocket.getOutputStream())
    private val inStream: BufferedInputStream = BufferedInputStream(clientSocket.getInputStream())
    private var running = true
    private lateinit var serde: ArylicSerde

    constructor(host: String, port: Int) : this(Socket(host, port))

    fun setSerde(serde: ArylicSerde) {
        this.serde = serde
    }

    fun startDataReader() {
        log.info { "Starting datareader" }
        var bgThread = GlobalScope.launch {
            try {
                while (running) {
                    if (!readData()) {
                        log.warn { "Stream closed!" }
                        running = false
                    }
                }
            } catch (e: IOException) {
                log.warn(e) { "IO exception" }
            }
        }
    }

    fun testMut(enabled: Boolean) {
        sendCommand(Command.Mute(enabled))
    }

    fun sendCommand(cmd: SentCommand) {
        out.write(serde.encode(cmd).toByteArray())
        out.flush()
    }

    fun readData(): Boolean {
        val buf = ByteArray(2048)
        val size = inStream.read(buf)
        return if (size == -1) {
            log.warn { "No data to read" }
            false
        } else {
            val udata = UData(buf, size)
            serde.decode(udata){}
            true
        }
    }

//    fun expect() {
//        Promise
//    }


}