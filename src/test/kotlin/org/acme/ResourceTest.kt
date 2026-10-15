package org.acme

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.every
import io.mockk.mockk
import io.quarkiverse.test.junit.mockk.InjectMock
import io.quarkus.test.common.http.TestHTTPEndpoint
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.vertx.core.Future
import jakarta.inject.Inject
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.URI
import java.util.concurrent.TimeUnit


@QuarkusTest
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class ResourceTest {

    @InjectMock
    private lateinit var mockController: Controller

    @TestHTTPEndpoint(ArylicResourceToplevel::class)
    @TestHTTPResource
    lateinit var urlArylicToplevel: URI
    lateinit var urlDevice: URI

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Inject
    private lateinit var objectMapper: ObjectMapper
    private val mockConnection = mockk<ArylicConnection>(relaxed = true)
    private val deviceInfo = Command.DeviceInfo("apSsid", "type", "name", "routerSsid", 2, 4, 6)

    @BeforeEach
    fun setupMocks() {
        urlDevice = urlArylicToplevel.resolve("arylic/device1/")
        every { mockConnection.device } returns Device("fakehost.mock", "testdevice")
        every { mockController.getConnections() } returns mapOf("device1" to mockConnection)
        every { mockController.getConnection("device1") } returns mockConnection
        every { mockController.getConnection("invalidDevice") } returns null
        every { mockConnection.expect(Command.DeviceInfo::class.java) } returns Future.succeededFuture(deviceInfo)
    }

    @Test
    fun testToplevel() {
        log.debug { "testToplevel" }
        val res = ArylicResourceToplevel.DeviceRest("device1", "fakehost.mock")
        val resAsString = objectMapper.writeValueAsString(listOf(res))
        RestAssured.given()
            .`when`()
            .get(urlArylicToplevel)
            .then()
            .statusCode(200)
            .body(`is`(resAsString))
    }

    @Test
    fun testInvalidDevice() {
        RestAssured.given()
            .`when`()
            .get(urlArylicToplevel.resolve("arylic/invalidDevice/device-info"))
            .then()
            .statusCode(404)
    }

    private fun deviceOk(path: String, res: Any, post: Boolean = false) {
        val resAsString = objectMapper.writeValueAsString(res)
        val url = urlDevice.resolve(path)
        log.debug { "url: $url" }
        val request = RestAssured.given().`when`()
        (if (post) request.post(url) else request.get(url))
            .then()
            .statusCode(200)
            .header(HttpHeaders.CONTENT_TYPE, "${MediaType.APPLICATION_JSON};charset=UTF-8")
            .body(`is`(resAsString))
    }

    @Test
    fun testDeviceInfo() {
        deviceOk("device-info", deviceInfo)
    }

    @Test
    fun testMute() {
        deviceOk("mute", ArylicResource.Muted("device1", true), post = true)
    }

    @Test
    fun testUnmute() {
        deviceOk("unmute", ArylicResource.Muted("device1", false), post = true)
    }

    @Test
    fun testMetadata() {
        val data = Command.Data("title", "artist", "album", "vendor", 5)
        every { mockConnection.expect(Command.Data::class.java) } returns Future.succeededFuture(data)
        deviceOk("metadata", data)
    }

    @Test
    fun testStatus() {
        val data = Command.PlayInfo(
            "type",
            "ch",
            "mode",
            "loop",
            "eq",
            "status",
            "curpos",
            "offsetPts",
            "totlen",
            "title",
            "artist",
            "album",
            "alarmflag",
            "plicount",
            "plicurr",
            "vol",
            "mute"
        )
        every { mockConnection.expect(Command.PlayInfo::class.java) } returns Future.succeededFuture(data)
        deviceOk("status", data)
    }

    @Test
    fun testPlaypause() {
        val data = Command.PlayStatus(true)
        every { mockConnection.expect(Command.PlayStatus::class.java) } returns Future.succeededFuture(data)
        val res = ArylicResource.PlayStatusRest("device1", true)
        deviceOk("playpause", res, post = true)
    }

    @Test
    fun testPlay() {
        val data = ArylicResource.PlayStatusRest("device1", true)
        deviceOk("play", data, post = true)
    }

    @Test
    fun testPause() {
        val data = ArylicResource.PlayStatusRest("device1", false)
        deviceOk("pause", data, post = true)
    }

    @Test
    fun testPreset() {
        deviceOk("preset/3", mapOf("device" to "device1", "preset" to 3), post = true)
    }

    @Test
    fun testStateChangeRejectsGet() {
        RestAssured.given()
            .`when`()
            .get(urlDevice.resolve("mute"))
            .then()
            .statusCode(anyOf(`is`(404), `is`(405)))
    }
}
