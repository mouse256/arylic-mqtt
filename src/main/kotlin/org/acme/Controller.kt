package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import io.vertx.core.Vertx
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import xyz.gianlu.zeroconf.Zeroconf
import java.util.concurrent.Callable
import kotlin.time.Duration.Companion.seconds


@ApplicationScoped
class Controller : ArylicConnection.Callbacks {
    private val log = KotlinLogging.logger {}

    @Inject
    lateinit var serde: ArylicSerde

    @Inject
    lateinit var arylicConfig: ArylicConfig

    /**
     * Discovered devices.
     * Boolean indicates connection status
     */
    private val discoveredDevices = mutableSetOf<Device>()

    /** establised connection */
    private val connections = mutableMapOf<String, ArylicConnection>()

    @Inject
    lateinit var mqtt: Mqtt

    @Inject
    lateinit var vertx: Vertx
    var zeroconf: Zeroconf? = null

    fun onStart(@Observes ev: StartupEvent) {
        log.info { "onStart. Discovery time: ${arylicConfig.discoveryTimer()}" }
        vertx.setPeriodic(0, arylicConfig.discoveryTimer().toMillis()) { _ ->
            log.debug { "re-connecting" }
            arylicConfig.devices()
                .map { d -> Device(d.ip(), "unkown", d.port().orElse(Device.DEFAULT_PORT)) }
                .forEach { tryConnect(it) }
        }
        vertx.setPeriodic(10.seconds.inWholeMilliseconds, arylicConfig.pingTimer().toMillis()) { _ ->
            pingAll()
        }
        mqtt.setController(this)

        if (arylicConfig.autoDiscovery().enabled().orElse(true)) {
            discover()
        } else {
            log.info { "Auto-discovery disabled" }
        }
    }

    fun onStop(@Observes ev: ShutdownEvent) {
        zeroconf?.close()
    }

    private fun discover() {
        log.info { "Starting auto-discovery" }
        val zc = Zeroconf()
        zeroconf = zc
        zc.setUseIpv4(true)
            .setUseIpv6(false)
            .addAllNetworkInterfaces()

        // Start discovering
        val services = zc.discover("linkplay", "tcp", ".local")

        vertx.setPeriodic(1000) {
            log.debug { "zeroconf devices: ${services.services}" }
            synchronized(discoveredDevices) {
                val newDiscoved = services.services.toList().stream()
                    .map { d -> Device(d.target, d.name) }
                    .toList()
                log.debug { "1: Existing devices: $discoveredDevices -- Discovered devices: $newDiscoved" }

                //new ones
                newDiscoved
                    .filter { d -> !discoveredDevices.contains(d) }
                    .forEach { d ->
                        log.info { "Discovered new device: $d" }
                        discoveredDevices.add(d)
                        sendHomeAssistantDiscovery(d)
                        tryConnect(d)
                    }

                //lost devices
                discoveredDevices
                    .filter { d -> !discoveredDevices.contains(d) }
                    .forEach { d -> log.info { "Lost device: $d" } }
                //seems to happen from time to time. Remove if we get a connection lost exception.
//                    .removeIf { d ->
//                        if (!newDiscoved.contains(d)) {
//                            log.debug { "2: Existing devices: $discoveredDevices -- Discovered devices: $newDiscoved" }
//                            log.info { "Lost device: $d" }
//                            mqtt.sendAvailability(d.name.lowercase(), false)
//                            true
//                        } else {
//                            false
//                        }
//                   }
            }
        }
    }

    private fun sendHomeAssistantDiscovery(cfg: Device) {
        mqtt.sendHomeAssistant(cfg)
    }


    private fun tryConnect(cfg: Device) {
        log.debug { "tryConnect" }
        synchronized(connections) {
            for (conn in connections) {
                if (conn.value.device == cfg) {
                    log.debug { "Ip ${cfg.host} is already connected" }
                    return
                }
            }
        }

        vertx.executeBlocking(Callable {
            try {
                log.info { "Connecting to device on \"${cfg.host}:${cfg.port}\"" }
                val conn = ArylicConnection(cfg, this@Controller)
                conn.setSerde(serde)
                conn.startDataReader()
                conn.expect(Command.DeviceInfo::class.java)
                    .onSuccess { addDevice(conn, it) }
                    .onFailure { log.warn { "Can't fetch initial device-info" } }
                conn.sendCommand(Command.DeviceInfoCmd)
                log.info { "Requesting deviceInfo from \"${cfg.host}:${cfg.port}\"" }
            } catch (ex: Exception) {
                log.warn { "Unable to connect to device ${cfg.host}: ${ex.message}" }
            }
        })
    }

    private fun pingAll() {
        log.debug { "pinging devices" }
        synchronized(connections) {
            connections.forEach {
                log.debug { "Pinging ${it.key}" }
                it.value.ping()
            }
        }
    }

    private fun addDevice(conn: ArylicConnection, deviceInfo: Command.DeviceInfo) {
        log.info { "Connected to device ${deviceInfo.name} on \"${conn.device.host}\"" }
        mqtt.sendAvailability(deviceInfo.name.lowercase(), true)
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

    fun getConnections(): Map<String, ArylicConnection> {
        synchronized(connections) {
            return connections.toMap()
        }
    }


    override fun onDisconnected(device: Device) {
        log.info { "onDisconnected: ${device.host}" }
        synchronized(connections) {
            val name = connections.filter { e -> e.value.device == device }.map { e -> e.key }.firstOrNull()
            name?.let { connections.remove(it) }
        }
        synchronized(discoveredDevices) {
            discoveredDevices.remove(device)
            mqtt.sendAvailability(device.name.lowercase(), false)
        }
    }

}