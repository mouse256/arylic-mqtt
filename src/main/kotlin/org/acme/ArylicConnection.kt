package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.Future
import io.vertx.core.Promise
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration.Companion.seconds


/**
 * https://forum.arylic.com/t/latest-api-documents-and-uart-protocols/534/3
 * https://drive.google.com/file/d/1prKuVjpE0A9nSeNt_YiN5KeQOgkvTB6G/view
 */
@OptIn(ExperimentalUnsignedTypes::class)
class ArylicConnection(val device: Device, socket: Socket, val cb: Callbacks) {

    private val log = KotlinLogging.logger {}

    private val clientSocket = socket
    private val out: BufferedOutputStream = BufferedOutputStream(clientSocket.getOutputStream())
    private val inStream: BufferedInputStream = BufferedInputStream(clientSocket.getInputStream())
    private var running = true
    private lateinit var serde: ArylicSerde
    private val listeners = mutableListOf<(ReceiveCommand) -> Boolean>()
    private var handler: ((ReceiveCommand) -> Unit)? = null

    companion object {
        private fun createSocket(host: String, port: Int): Socket {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 5.seconds.inWholeMilliseconds.toInt())
            return socket
        }
    }

    constructor(device: Device, cb: Callbacks) : this(device, createSocket(device.host, device.port), cb)

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
            } finally {
                running = false
                cb.onDisconnected(device)
            }
        }
    }

    fun sendCommand(cmd: SentCommand) {
        log.debug { "Sending command: ${cmd.javaClass.name} to ${this.device.host}" }
        synchronized(this) {
            log.debug { "MSG: ${Helper.bytesToHex(serde.encode(cmd).toByteArray())}" }
            out.write(serde.encode(cmd).toByteArray())
            out.flush()
            log.debug{ "command sent to ${this.device.host}" }
        }
    }

    fun ping() {
        //TODO: seems to break things
        //sendCommand(Command.PlaybackStatus)
        sendCommand(Command.PlayStatus(true))
        //synchronized(this) {
            //out.write(byteArrayOf(ArylicSerde.LF.toByte()))
            //out.flush()
        //}
    }

    private fun readData(): Boolean {
        val buf = ByteArray(2048)
        val size = inStream.read(buf)
        return if (size == -1) {
            log.warn { "No data to read" }
            false
        } else {
            val udata = UData(buf, size)
            serde.decode(udata, this::handle)
            true
        }
    }

    private fun handle(cmd: ReceiveCommand) {
        log.info { "Received command: $cmd" }
        handler?.invoke(cmd)
        synchronized(listeners) {
            listeners.filter { it(cmd) }
                .toList()
                .forEach { listeners.remove(it) }
        }
    }

    fun <T : ReceiveCommand> expect(clazz: Class<T>): Future<T> {
        val promise = Promise.promise<T>()
        val listener: ((ReceiveCommand) -> Boolean) = { cmd: ReceiveCommand ->
            if (clazz.isInstance(cmd)) {
                @Suppress("UNCHECKED_CAST")
                promise.complete(cmd as T)
                true
            } else {
                false
            }
        }
        synchronized(listeners) {
            listeners.add(listener)
        }
        return promise.future();
    }

    fun setHandler(handler: (ReceiveCommand) -> Unit) {
        this.handler = handler
    }

    interface Callbacks {
        fun onDisconnected(device: Device)
    }
}

data class Device(val host: String, val port: Int = DEFAULT_PORT) {
    companion object {
        val DEFAULT_PORT = 8899
    }
}
