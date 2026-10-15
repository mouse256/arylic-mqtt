package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.Future
import io.vertx.core.Promise
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
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

    /**
     * Plays a stream URL via the standard UPnP AVTransport renderer (port 49152), which works on all
     * devices, including secure-mode ones (e.g. H50) that don't expose the plain HTTP API.
     */
    fun playStream(url: String) {
        val escapedUrl = url.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        log.info { "Playing stream via UPnP AVTransport: $url" }
        upnpAvTransport("SetAVTransportURI", "<InstanceID>0</InstanceID><CurrentURI>$escapedUrl</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>")
        upnpAvTransport("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }

    private fun upnpAvTransport(action: String, args: String) {
        val serviceType = "urn:schemas-upnp-org:service:AVTransport:1"
        val soap = """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="$serviceType">$args</u:$action></s:Body></s:Envelope>"""
        val conn = URL("http://${device.host}:49152/upnp/control/rendertransport1").openConnection() as HttpURLConnection
        conn.connectTimeout = 5.seconds.inWholeMilliseconds.toInt()
        conn.readTimeout = 10.seconds.inWholeMilliseconds.toInt()
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        conn.setRequestProperty("SOAPAction", "\"$serviceType#$action\"")
        conn.doOutput = true
        try {
            conn.outputStream.use { it.write(soap.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                throw IOException("UPnP $action failed (${conn.responseCode}): $err")
            }
            log.debug { "UPnP $action OK" }
        } finally {
            conn.disconnect()
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

data class Device(val host: String, val name: String, val port: Int = DEFAULT_PORT) {
    companion object {
        val DEFAULT_PORT = 8899
    }
}
