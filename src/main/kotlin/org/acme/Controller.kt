package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.runtime.StartupEvent
import io.vertx.core.Vertx
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject

@ApplicationScoped
class Controller : ArylicConnection.Callbacks {
    private val log = KotlinLogging.logger {}

    @Inject
    lateinit var serde: ArylicSerde

    @Inject
    lateinit var arylicConfig: ArylicConfig
    private val connections = mutableMapOf<String, ArylicConnection>()

    @Inject
    lateinit var mqtt: Mqtt

    @Inject
    lateinit var vertx: Vertx

    fun onStart(@Observes ev: StartupEvent) {
        log.info { "onStart" }
        vertx.setPeriodic(0, arylicConfig.discoveryTimer().toMillis()) { _ ->
            log.info { "re-connecting" }
            arylicConfig.devices()
                .forEach { tryConnect(it) }
        }
        mqtt.setController(this)
    }

    private fun tryConnect(cfg: ArylicConfig.Device) {
        synchronized(connections) {
            for (conn in connections) {
                if (conn.value.host == cfg.ip()) {
                    log.debug { "Ip ${cfg.ip()} is already connected" }
                    return
                }
            }
        }
        try {
            val port = cfg.port().orElse(8899)
            log.info { "Adding device \"${cfg.ip()}:$port\"" }
            val conn = ArylicConnection(cfg.ip(), port, this)
            conn.setSerde(serde)
            conn.startDataReader()
            conn.expect(Command.DeviceInfo::class.java)
                .onSuccess { addDevice(conn, it) }
                .onFailure { log.warn { "Can't fetch initial device-info" } }
            conn.sendCommand(Command.DeviceInfoCmd)
        } catch (ex: Exception) {
            log.warn { "Unable to connect to device ${cfg.ip()}: ${ex.message}" }
        }

    }

    private fun addDevice(conn: ArylicConnection, deviceInfo: Command.DeviceInfo) {
        log.info { "Discovered device ${deviceInfo.name} with ip \"${conn.host}\"" }
        conn.setHandler { cmd -> mqtt.handle(deviceInfo.name, cmd) }
        synchronized(connections) {
            connections[deviceInfo.name.lowercase()] = conn
        }
    }

    fun getConnection(name: String): ArylicConnection? {
        synchronized(connections) {
            return connections[name.lowercase()]
        }
    }


    override fun onDisconnected(host: String) {
        log.info { "onDisconnected: $host" }
        synchronized(connections) {
            val name = connections.filter { e -> e.value.host == host }.map { e -> e.key }.firstOrNull()
            name?.let { connections.remove(it) }
        }
    }

}