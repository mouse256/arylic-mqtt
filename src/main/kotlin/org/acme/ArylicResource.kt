package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.config.inject.ConfigProperty

@Path("/arylic")
class ArylicResource {

    //private val arylicConnection = ArylicConnection("192.168.1.185", 8899) //eetkamer

    @Inject
    lateinit var serde: ArylicSerde

    private val arylicConnection = ArylicConnection("192.168.1.74", 8899)
    private val log = KotlinLogging.logger {}

    @PostConstruct
    fun postConstruct() {
        log.info { "postConstruct" }
        arylicConnection.setSerde(serde)
        arylicConnection.test()
    }

    @GET
    @Path("init")
    @Produces(MediaType.TEXT_PLAIN)
    fun hello(): String {
        log.info { "hello" }
        arylicConnection.test()
        return "Hello from RESTEasy Reactive"
    }

    @GET
    @Path("mute")
    @Produces(MediaType.TEXT_PLAIN)
    fun mute(): String {
        log.info { "mute" }
        arylicConnection.testMut(true)
        return "Mute!"
    }

    @GET
    @Path("unmute")
    @Produces(MediaType.TEXT_PLAIN)
    fun unmute(): String {
        log.info { "unmute" }
        arylicConnection.testMut(false)
        return "UnMute!"
    }

    @GET
    @Path("device-info")
    @Produces(MediaType.TEXT_PLAIN)
    fun deviceInfo(): String {
        log.info { "device-info" }
        arylicConnection.sendCommand(Command.DeviceInfo)
        return "deviceInfo"
    }

    @GET
    @Path("metadata")
    @Produces(MediaType.TEXT_PLAIN)
    fun metadata(): String {
        log.info { "playback metadata" }
        arylicConnection.sendCommand(Command.PlaybackMetadata)
        return "playback metadata"
    }

    @GET
    @Path("status")
    @Produces(MediaType.TEXT_PLAIN)
    fun status(): String {
        log.info { "playback status" }
        arylicConnection.sendCommand(Command.PlaybackStatus)
        return "playback status"
    }
}