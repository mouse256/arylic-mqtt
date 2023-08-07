package org.acme

import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Test
import java.lang.IllegalStateException

@OptIn(ExperimentalUnsignedTypes::class)
@QuarkusTest
class SerdeTest {

    private fun stringToUByte(input: String): UByteArray {
        return input.split(' ')
            .map {
                if (it.length != 2) {
                    throw IllegalStateException("size != 2: $it")
                }
                it.toUByte(16)
            }.toUByteArray()
    }

    @Test
    fun test1() {
        val data = stringToUByte("18 96 18 20 0C 00 00 00 D7 02 00 00 00 00 00 00 00 00 00 00 41 58 58 2B 4D 55 54 2B 30 30 30 0A")
        //val data = stringToUByte("0C")
        ArylicSerde().decode(UData(data))
    }
    //

    //18 96 18 20 A7 00 00 00 C5 28 00 00 00 00 00 00 00 00 00 00 41 58 58 2B 4D 45 41 2B 44 41 54 7B 20 22 74 69 74 6C 65 22 3A 20 22 35 32 36 31 36 44 36 44 36 43 36 39 36 35 36 34 22 2C 20 22 61 72 74 69 73 74 22 3A 20 22 34 31 34 32 43 33 38 39 34 43 34 31 35 32 34 34 22 2C 20 22 61 6C 62 75 6D 22 3A 20 22 35 32 36 31 36 44 36 44 37 33 37 34 36 35 36 39 36 45 32 30 36 46 36 45 32 30 35 30 36 39 36 31 36 45 36 46 22 2C 20 22 76 65 6E 64 6F 72 22 3A 20 22 35 33 37 30 36 46 37 34 36 39 36 36 37 39 22 2C 20 22 73 6B 69 70 6C 69 6D 69 74 22 3A 20 30 20 7D 26 0A
}