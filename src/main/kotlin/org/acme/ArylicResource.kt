package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.vertx.UniHelper
import io.vertx.core.Future
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType

@Path("/arylic/{device}")
class ArylicResource {

    private val log = KotlinLogging.logger {}

    @Inject
    lateinit var controller: Controller

    private fun getDevice(device: String): ArylicConnection {
        return controller.getConnection(device) ?: throw NotFoundException("Device with name $device not found")
    }

    data class Muted(val device: String, val muted: Boolean)

    @GET
    @Path("mute")
    @Produces(MediaType.APPLICATION_JSON)
    fun mute(@PathParam("device") device: String): Muted {
        log.info { "mute" }
        getDevice(device).sendCommand(Command.Mute(true))
        return Muted(device, true)
    }

    @GET
    @Path("unmute")
    @Produces(MediaType.APPLICATION_JSON)
    fun unmute(@PathParam("device") device: String): Muted {
        log.info { "unmute" }
        getDevice(device).sendCommand(Command.Mute(false))
        return Muted(device, false)
    }

    @GET
    @Path("device-info")
    @Produces(MediaType.APPLICATION_JSON)
    fun deviceInfo(@PathParam("device") device: String): Uni<Command.DeviceInfo> {
        log.info { "device-info" }
        val conn = getDevice(device)
        val fut = conn.expect(Command.DeviceInfo::class.java)
        conn.sendCommand(Command.DeviceInfoCmd)
        return UniHelper.toUni(fut)
    }

    @GET
    @Path("metadata")
    @Produces(MediaType.APPLICATION_JSON)
    fun metadata(@PathParam("device") device: String): Uni<Command.Data> {
        log.info { "playback metadata" }
        val conn = getDevice(device)
        val fut = conn.expect(Command.Data::class.java)
        conn.sendCommand(Command.PlaybackMetadata)

        return UniHelper.toUni(fut)
    }

    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    fun status(@PathParam("device") device: String): Uni<Command.PlayInfo> {
        log.info { "playback status" }
        val conn = getDevice(device)
        val fut = conn.expect(Command.PlayInfo::class.java)
        conn.sendCommand(Command.PlaybackStatus)

        return UniHelper.toUni(fut)
    }

    data class PlayStatusRest(val device: String, val playing: Boolean)

    @GET
    @Path("playpause")
    @Produces(MediaType.APPLICATION_JSON)
    fun playPause(@PathParam("device") device: String): Uni<PlayStatusRest> {
        log.info { "play/pause" }
        val conn = getDevice(device)
        val fut = conn.expect(Command.PlayStatus::class.java)
        conn.sendCommand(Command.PlayPause)

        return UniHelper.toUni(fut.compose { status -> Future.succeededFuture(PlayStatusRest(device, status.playing)) })
    }

    @GET
    @Path("play")
    @Produces(MediaType.APPLICATION_JSON)
    fun play(@PathParam("device") device: String): PlayStatusRest {
        log.info { "play" }
        val conn = getDevice(device)
        conn.sendCommand(Command.Play) //won't reply if there is no change

        return PlayStatusRest(device, true)
    }

    @GET
    @Path("pause")
    @Produces(MediaType.APPLICATION_JSON)
    fun pause(@PathParam("device") device: String): PlayStatusRest {
        log.info { "pause" }
        val conn = getDevice(device)
        conn.sendCommand(Command.Pause)

        return PlayStatusRest(device, false)
    }
}