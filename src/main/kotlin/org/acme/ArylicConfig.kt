package org.acme

import io.smallrye.config.ConfigMapping
import java.util.*


@ConfigMapping(prefix = "arylic")
interface ArylicConfig {
    fun devices(): Set<Device>

    fun discoveryTimer(): java.time.Duration
    fun pingTimer(): java.time.Duration
    fun autoDiscovery(): AutoDiscovery

    interface Device {
        //fun name(): String
        fun ip(): String

        fun port(): Optional<Int>
    }

    interface AutoDiscovery {
        fun enabled(): Optional<Boolean>
    }

}