package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.uni
import io.vertx.core.Future
import io.vertx.core.Promise
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.config.inject.ConfigProperty
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
    fun deviceInfo(@PathParam("device") device: String): String {
        log.info { "device-info" }
        getDevice(device).sendCommand(Command.DeviceInfo)
        return "$device unmuted\n"
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
    fun status(@PathParam("device") device: String): CompletableFuture<Command.Inf> {
        log.info { "playback status" }
        val conn = getDevice(device)
        val fut = conn.expect(Command.Inf::class.java)
        conn.sendCommand(Command.PlaybackStatus)

        return asCompletableFuture(fut)
    }

    private fun<T: Any> asCompletableFuture(fut: Future<T>): CompletableFuture<T> {
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