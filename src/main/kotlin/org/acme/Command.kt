@file:OptIn(ExperimentalUnsignedTypes::class)

package org.acme

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

sealed interface ReceiveCommand : Command {
    fun name(): String
}

sealed interface SentCommand : Command {

    fun toPayload(): UByteArray
}

sealed class CommandHelper {
    fun merge3cmd(part1: UByteArray, part2: UByteArray, part3: UByteArray): UByteArray {
        return part1 + ArylicSerde.PLUS + part2 + ArylicSerde.PLUS + part3 + ArylicSerde.LF

    }
}

sealed interface Command {
    data class Mute(val enabled: Boolean) : SentCommand, ReceiveCommand, CommandHelper() {
        /**
         * MCU+MUT+NNN
         * Mute, '000' to unmute, '001' to mute, will return mute status
         */
        override fun toPayload(): UByteArray {
            log.debug { "Mut to payload" }
            val data = if (enabled) ArylicSerde.MUT_MUTE else ArylicSerde.MUT_UNMUTE
            return merge3cmd(ArylicSerde.MCU, ArylicSerde.MUT, data)
        }

        override fun name(): String {
            return "Mute"
        }
    }

    data class Volume(val volume: Int) : SentCommand, ReceiveCommand, CommandHelper() {
        /**
         * MCU+VOL+NNN
         * Get/set volumne. 0 <= NNN <= 100
         */
        override fun toPayload(): UByteArray {
            val volStr = String.format("%03d", volume)
            log.debug { "VOL to payload: $volStr" }

            return ArylicSerde.MCU + ArylicSerde.PLUS + ArylicSerde.VOL + ArylicSerde.PLUS + volStr.toByteArray().toUByteArray() + ArylicSerde.LF
        }

        override fun name(): String {
            return "Volume"
        }
    }

    data class Data(
        val title: String,
        val artist: String,
        val album: String,
        val vendor: String,
        @JsonProperty("skiplimit") val skipLimit: Int

    ) : ReceiveCommand {
        override fun name(): String {
            return "Data"
        }
    }

    data class DeviceInfo(
        ////AXX+DEV+INFSoundSystem_B706;release;Bureau;;0;0;0&
        val apSsid: String,
        val type: String,
        val name: String,
        val routerSsid: String,
        val signalStrength: Int,
        val batteryState: Int,
        val batteryValue: Int
    ) : ReceiveCommand {
        override fun name(): String {
            return "DeviceInfo"
        }

        fun toPayload(): UByteArray {
            log.debug { "device-info to payload" }
            return DeviceInfoCmd.merge3cmd(ArylicSerde.AXX, ArylicSerde.DEV, ArylicSerde.INF) +
                    "$apSsid;$type;$name;$routerSsid;$signalStrength;$batteryState;$batteryValue&\n"
                        .toByteArray().toUByteArray()
        }
    }

    data class PlayInfo(
        val type: String,
        val ch: String,
        val mode: String,
        val loop: String,
        val eq: String,
        val status: String,
        val curpos: String,
        @JsonProperty("offset_pts") val offsetPts: String,
        val totlen: String,
        @JsonProperty("Title") val title: String,
        @JsonProperty("Artist") val artist: String,
        @JsonProperty("Album") val album: String,
        val alarmflag: String,
        val plicount: String,
        val plicurr: String,
        val vol: String,
        val mute: String,
    ) : ReceiveCommand {
        override fun name(): String {
            return "PlayInfo"
        }
    }

    data class PlayStatus(
        val playing: Boolean
    ) : ReceiveCommand, SentCommand {
        override fun name(): String {
            return "PlayStatus"
        }

        /**
         * MCU+PLY+GET
         * Return PlayStatus
         */
        override fun toPayload(): UByteArray {
            log.debug { "PlayStatus to payload" }
            return DeviceInfoCmd.merge3cmd(ArylicSerde.MCU, ArylicSerde.PLY, ArylicSerde.GET)
        }
    }


    object DeviceInfoCmd : SentCommand, CommandHelper() {
        /**
         * MCU+DEV+GET
         * Return device information
         */
        override fun toPayload(): UByteArray {
            log.debug { "device-info to payload" }
            return merge3cmd(ArylicSerde.MCU, ArylicSerde.DEV, ArylicSerde.GET)
        }
    }

    object Play : SentCommand, CommandHelper() {
        /**
         * MCU+PLY-PLA
         * play, will return mute and play status when changed to playing
         * state, or else no return
         */
        override fun toPayload(): UByteArray {
            log.debug { "play to payload" }
            return ArylicSerde.MCU + ArylicSerde.PLUS + ArylicSerde.PLY + ArylicSerde.MINUS + ArylicSerde.PLA + ArylicSerde.LF
        }
    }

    object Pause : SentCommand, CommandHelper() {
        /**
         * MCU+PLY-PUS pause
         * will return mute and play status when changed to pause
         * or else no return
         */
        override fun toPayload(): UByteArray {
            log.debug { "pause to payload" }
            return ArylicSerde.MCU + ArylicSerde.PLUS + ArylicSerde.PLY + ArylicSerde.MINUS + ArylicSerde.PUS + ArylicSerde.LF
        }
    }

    object PlayPause : SentCommand, CommandHelper() {
        /**
         * MCU+PLY+PUS play/pause, will return mute and play status
         */
        override fun toPayload(): UByteArray {
            log.debug { "play/pause to payload" }
            return ArylicSerde.MCU + ArylicSerde.PLUS + ArylicSerde.PLY + ArylicSerde.PLUS + ArylicSerde.PUS + ArylicSerde.LF
        }
    }

    object PlaybackMetadata : SentCommand, CommandHelper() {
        /**
         * MCU+MEA+GET
         * Return device information
         */
        override fun toPayload(): UByteArray {
            log.debug { "playback metadata to payload" }
            return merge3cmd(ArylicSerde.MCU, ArylicSerde.MEA, ArylicSerde.GET)
        }
    }

    object PlaybackStatus : SentCommand, CommandHelper() {
        /**
         * MCU+PINFGET
         * Request for playback status. Will return AXX+PLY+INF
         */
        override fun toPayload(): UByteArray {
            log.debug { "playback status to payload" }
            return ArylicSerde.MCU + ArylicSerde.PLUS + ArylicSerde.PINFGET
        }
    }

    object Ready : ReceiveCommand {
        override fun name(): String {
            return "Ready"
        }
    }
}