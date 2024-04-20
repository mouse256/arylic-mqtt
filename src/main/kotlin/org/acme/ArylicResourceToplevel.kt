package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import kotlinx.serialization.Serializable

@Path("/arylic")
class ArylicResourceToplevel {

    private val log = KotlinLogging.logger {}

    @Inject
    lateinit var controller: Controller

    private fun getDevice(device: String): ArylicConnection {
        return controller.getConnection(device) ?: throw NotFoundException("Device with name $device not found")
    }

    @Serializable
    data class DeviceRest(val id: String, val host: String)

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getDevices(): List<DeviceRest> {
        log.info { "getDevices" }
        return controller.getConnections().map { d -> DeviceRest(d.key, d.value.device.host) }.toList()
    }


}