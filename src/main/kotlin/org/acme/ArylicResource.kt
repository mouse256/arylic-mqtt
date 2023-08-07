package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/arylic")
class ArylicResource {

    //private val arylicConnection = ArylicConnection("192.168.1.185", 8899) //eetkamer
    private val arylicConnection = ArylicConnection("192.168.1.74", 8899)
    private val log = KotlinLogging.logger {}

    @GET
    @Path("init")
    @Produces(MediaType.TEXT_PLAIN)
    fun hello(): String {
        log.info { "hello" }
        arylicConnection.test()
        return "Hello from RESTEasy Reactive"
    }
}