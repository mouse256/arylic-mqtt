package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager.TestInjector
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager.TestInjector.AnnotatedAndMatchesType
import java.lang.RuntimeException
import java.net.BindException
import java.net.ServerSocket


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class MockSocket

class SocketTestResource : QuarkusTestResourceLifecycleManager {
    var socket: TestSocketWrapper? = null
    var port = 40022

    companion object {
        private val log = KotlinLogging.logger {}
    }


    override fun start(): Map<String, String> {
        log.info { "finding free port for socket stub server" }
        for (i in 1..100) {
            try {
                socket = TestSocketWrapper(port)
                break
            } catch (ex: BindException) {
                if (i == 10) {
                    throw RuntimeException("Can't find a free port. Last tried $port", ex)
                }
                port += 1
            }
        }

        log.info { "Starting socket stub server on port $port" }

        // create some stubs
        return mapOf(
            "arylic.discovery-timer" to "PT1s",
            "arylic.devices[0].ip" to "127.0.0.1",
            "arylic.devices[0].port" to port.toString(),
        )
    }

    @Synchronized
    override fun stop() {
        log.info { "Stopping socket stub server" }
        socket?.close()
    }

    override fun inject(testInjector: TestInjector) {
        testInjector.injectIntoFields(
            socket,
            AnnotatedAndMatchesType(MockSocket::class.java, TestSocketWrapper::class.java)
        )
    }
}

class TestSocketWrapper private constructor(private var sock: ServerSocket, private var port: Int): AutoCloseable {

    constructor(port: Int): this(ServerSocket(port), port) {

    }
    companion object {
        private val log = KotlinLogging.logger {}
    }
    fun getSocket(): ServerSocket {
        if (sock.isClosed) {
            log.info { "Creating new socket" }
            sock = ServerSocket(port)
        }
        return sock
    }

    override fun close() {
        sock.close()
    }
}