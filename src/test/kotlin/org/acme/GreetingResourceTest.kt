package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
@QuarkusTest
class GreetingResourceTest {
    private val log = KotlinLogging.logger {}

    @Test
    fun testHelloEndpoint() {
        log.info { "xx" }
        given()
          .`when`().get("/hello")
          .then()
             .statusCode(200)
             .body(`is`("Hello from RESTEasy Reactive"))
    }

}