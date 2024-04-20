package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.netty.handler.codec.mqtt.MqttConnectPayload
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.lang.RuntimeException
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalUnsignedTypes::class)
@QuarkusTest
@Timeout(value = 10, unit = TimeUnit.SECONDS)
//@QuarkusTestResource(SocketTestResource::class)
@TestProfile(MockSocketProfile::class)
class ControllerTest {
    @Inject
    lateinit var controller: Controller

    @MockSocket
    lateinit var ss: TestSocketWrapper

    val deviceName = "mock"
    var running = true
    var outStream: BufferedOutputStream? = null
    var inStream: BufferedInputStream? = null

    val deviceInfoRequest = SerdeTest.stringToUByte(
        "18 96 18 20 0C 00 00 00 04 03 00 00 00 00 00 00 00 00 00 00 4D 43 55 2B 44 45 56 2B 47 45 54 0A"
    )

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private fun startServer(cb: suspend () -> Unit) {
        log.info { "v2" }
        runBlocking {
            launch {
                startListen()
            }
            withContext(Dispatchers.Default) {
                cb()
            }
        }
        log.info { "v4" }
    }

    suspend fun startListen() {
        log.info { "start listening on serverSocket" }
        withContext(Dispatchers.IO) {
            val socket = ss.getSocket().accept()
            log.info { "Socket connected" }
            outStream = BufferedOutputStream(socket.getOutputStream())
            inStream = BufferedInputStream(socket.getInputStream())
            running=true
            try {
                while (running) {
                    readData(inStream!!)
                }
            }catch (ex: SocketException) {
                if (running) {
                    throw RuntimeException("Did not expect socket exception at this point", ex)
                }
                log.debug { "Not running anymore, ignore this exception" }
            }
        }
        log.info { "Socket closed" }
    }

    private fun readData(inStream: BufferedInputStream) {
        val buf = ByteArray(2048)
        val size = inStream.read(buf)
        if (size == -1) {
            log.warn { "No data to read" }

        } else {
            val udata = UData(buf, size)
            log.info { "Server data: ${Helper.bytesToHex(udata.data)}" }
            if (udata.data.contentEquals(deviceInfoRequest)) {
                log.info { "DeviceInfo request!" }
                outStream!!.write(
                    encode(
                        Command.DeviceInfo(
                            "ssid", "Relese", deviceName,
                            "", 0, 0, 0
                        ).toPayload()
                    ).toByteArray()
                )
                outStream!!.flush()
            } else {
                log.info { "Unknown server request" }
            }
            //18 96 18 20 0C 00 00 00 04 03 00 00 00 00 00 00 00 00 00 00 4D 43 55 2B 44 45 56 2B 47 45 54 0A
            //serde.decode(udata, this::handle)
        }
    }

    fun encode(payload: UByteArray): UByteArray {
        val checksum = Helper.littleEndianEncode(payload.sumOf { it.toUInt() })
        val length = Helper.littleEndianEncode(payload.size.toUInt())
        val data = ArylicSerde.HEADER + length + checksum + ArylicSerde.RESERVED + payload
        log.debug { "encoded payload: " + Helper.bytesToHex(data) }
        return data
    }
    fun closeSocket() {
        running =false
        inStream?.close()
        outStream?.close()
        ss.close()
    }

    @Test()
    fun testRegular() {
        controller.getConnection(deviceName) shouldBe null
        startServer {
            eventually(5.seconds) {
                controller.getConnection(deviceName) shouldNotBe null
            }
            log.info { "Connection OK" }
            closeSocket()
        }
    }

    @Test()
    fun testConnectionBroken() {
        controller.getConnection(deviceName) shouldBe null
        startServer {
            eventually(5.seconds) {
                controller.getConnection(deviceName) shouldNotBe null
            }
            log.info { "Connection OK" }
            closeSocket()
            eventually(5.seconds) {
                controller.getConnection(deviceName) shouldBe null
            }
            startServer {
                eventually(5.seconds) {
                    controller.getConnection(deviceName) shouldNotBe null
                }
                log.info { "Connection 2 OK" }
                closeSocket()
            }
        }
    }
}

