package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.core.Future
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Path("/arylic/{device}")
class ArylicResource {

    private val log = KotlinLogging.logger {}

    @Inject
    lateinit var controller: Controller

    private fun getDevice(device: String): ArylicConnection {
        return controller.getConnection(device) ?: throw NotFoundException("Device with name $device not found")
    }

    @GET
    @Path("mute")
    @Produces(MediaType.TEXT_PLAIN)
    fun mute(@PathParam("device") device: String): String {
        log.info { "mute" }
        getDevice(device).sendCommand(Command.Mute(true))
        return "$device muted\n"
    }

    @GET
    @Path("unmute")
    @Produces(MediaType.TEXT_PLAIN)
    fun unmute(@PathParam("device") device: String): String {
        log.info { "unmute" }
        getDevice(device).sendCommand(Command.Mute(false))
        return "$device unmuted\n"
    }

    @GET
    @Path("device-info")
    @Produces(MediaType.TEXT_PLAIN)
    fun deviceInfo(@PathParam("device") device: String): CompletableFuture<Command.DeviceInfo> {
        log.info { "device-info" }
        val conn = getDevice(device)
        val fut = conn.expect(Command.DeviceInfo::class.java)
        conn.sendCommand(Command.DeviceInfoCmd)
        return asCompletableFuture(fut)
    }

    @GET
    @Path("metadata")
    @Produces(MediaType.TEXT_PLAIN)
    fun metadata(@PathParam("device") device: String): CompletableFuture<Command.Data> {
        log.info { "playback metadata" }
        val conn = getDevice(device)
        val fut = conn.expect(Command.Data::class.java)
        conn.sendCommand(Command.PlaybackMetadata)

        return asCompletableFuture(fut)
    }


    @GET
    @Path("status")
    @Produces(MediaType.TEXT_PLAIN)
    fun status(@PathParam("device") device: String): CompletableFuture<Command.PlayInfo> {
        log.info { "playback status" }
        val conn = getDevice(device)
        val fut = conn.expect(Command.PlayInfo::class.java)
        conn.sendCommand(Command.PlaybackStatus)

        return asCompletableFuture(fut)
    }

    @GET
    @Path("playpause")
    @Produces(MediaType.TEXT_PLAIN)
    fun playPause(@PathParam("device") device: String): CompletableFuture<Command.PlayStatus> {
        log.info { "play/pause" }
        val conn = getDevice(device)
        val fut = conn.expect(Command.PlayStatus::class.java)
        conn.sendCommand(Command.PlayPause)

        return asCompletableFuture(fut)
    }

    @GET
    @Path("play")
    @Produces(MediaType.TEXT_PLAIN)
    fun play(@PathParam("device") device: String): String {
        log.info { "play" }
        val conn = getDevice(device)
        conn.sendCommand(Command.Play) //won't reply if there is no change

        return "OK"
    }

    @GET
    @Path("pause")
    @Produces(MediaType.TEXT_PLAIN)
    fun pause(@PathParam("device") device: String): String {
        log.info { "pause" }
        val conn = getDevice(device)
        conn.sendCommand(Command.Pause)

        return "OK"
    }

    private fun <T : Any> asCompletableFuture(fut: Future<T>): CompletableFuture<T> {
        val cf = CompletableFuture<T>()
        fut.onComplete { ar ->
            if (ar.succeeded()) {
                cf.complete(ar.result())
            } else {
                cf.completeExceptionally(ar.cause())
            }
        }
        return cf.orTimeout(5, TimeUnit.SECONDS)
    }
}