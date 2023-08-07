package org.acme


import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.matchers.shouldBe
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
@QuarkusTest
class HelperTest{

    private val log = KotlinLogging.logger {}

    @Test
    fun testLittleEndian() {
        log.info { "test" }
        Helper.littleEndianInt(Helper.littleEndianEncode(5u)) shouldBe (5u)
        Helper.littleEndianInt(Helper.littleEndianEncode(300u)) shouldBe (300u)
        Helper.littleEndianInt(Helper.littleEndianEncode(300000u)) shouldBe (300000u)
        Helper.littleEndianInt(Helper.littleEndianEncode(30000000u)) shouldBe (30000000u)
    }

}