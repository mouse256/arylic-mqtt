package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.runtime.StartupEvent
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject

@ApplicationScoped
class Controller {
    private val log = KotlinLogging.logger {}

    @Inject
    lateinit var serde: ArylicSerde

    @Inject
    lateinit var arylicConfig: ArylicConfig
    val connections = mutableMapOf<String, ArylicConnection>()

    fun onStart(@Observes ev: StartupEvent) {
        log.info { "onStart" }
        arylicConfig.devices().forEach{
            log.info { "Adding device \"${it.name()}\" with ip \"${it.ip()}\"" }
            val conn = ArylicConnection(it.ip(), 8899)
            conn.setSerde(serde)
            conn.startDataReader()
            connections[it.name().lowercase()] = conn
        }
    }

    fun getConnection(name: String): ArylicConnection? {
        return connections[name.lowercase()]
    }
}