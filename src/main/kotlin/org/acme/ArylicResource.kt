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
        getDevice(device).testMut(true)
        return "$device muted\n"
    }

    @GET
    @Path("unmute")
    @Produces(MediaType.TEXT_PLAIN)
    fun unmute(@PathParam("device") device: String): String {
        log.info { "unmute" }
        getDevice(device).testMut(false)
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
    fun metadata(@PathParam("device") device: String): CompletableFuture<String> {
        log.info { "playback metadata" }
        //arylicConnection.sendCommand(Command.PlaybackMetadata)
        //return "playback metadata"
        val prom = Promise.promise<String>()
        prom.complete("blah5")
        return asCompletableFuture(prom.future())
    }

    fun asCompletableFuture(fut: Future<String>): CompletableFuture<String> {
        val cf = CompletableFuture<String>()
        fut.onComplete { ar ->
            if (ar.succeeded()) {
                cf.complete(ar.result())
            } else {
                cf.completeExceptionally(ar.cause())
            }
         }
        return cf
    }
//
//    @GET
//    @Path("status")
//    @Produces(MediaType.TEXT_PLAIN)
//    fun status(@PathParam("device") device: String): String {
//        log.info { "playback status" }
//        arylicConnection.sendCommand(Command.PlaybackStatus)
//        return "playback status"
//    }
}