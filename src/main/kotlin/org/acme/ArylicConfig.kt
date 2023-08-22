package org.acme

import io.smallrye.config.ConfigMapping
import java.util.*


@ConfigMapping(prefix = "arylic")
interface ArylicConfig {
    fun devices(): Set<Device>
    interface Device {
        fun name(): String
        fun ip(): String
    }

}