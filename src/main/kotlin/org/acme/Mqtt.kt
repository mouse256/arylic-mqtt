package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.handler.codec.mqtt.MqttQoS
import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithDefault
import io.smallrye.reactive.messaging.mqtt.MqttMessage
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.muizenhol.homeassistant.discovery.Discovery
import org.muizenhol.homeassistant.discovery.component.Light
import org.muizenhol.homeassistant.discovery.component.Switch
import java.util.concurrent.CompletionStage
import java.util.regex.Pattern


@ApplicationScoped
class Mqtt {
    private val log = KotlinLogging.logger {}

    private lateinit var controller: Controller
    private val PATTERN_TOPIC_CMD = Pattern.compile("^arylic/cmd/(.*)/(.*)$")

    @Inject
    @Channel("state")
    private lateinit var emitter: Emitter<Any>

    fun setController(controller: Controller) {
        log.info { "Init MQTT" }
        this.controller = controller
    }

    fun handle(device: String, cmd: ReceiveCommand) {
        send(device, cmd)
    }

    fun send(device: String, msg: ReceiveCommand) {
        val topicBase = "arylic/state/${device.lowercase()}"
        val topic = "${topicBase}/${msg.name().lowercase()}"
        log.info { "Sending to topic: $topic" }
        when (msg) {
            is Command.Volume -> emitter.send(MqttMessage.of(topic, msg.volume, MqttQoS.AT_LEAST_ONCE))
            is Command.PlayStatus -> emitter.send(
                MqttMessage.of(
                    "${topicBase}/playing",
                    msg.playing,
                    MqttQoS.AT_LEAST_ONCE,
                    true
                )
            )

            else -> emitter.send(MqttMessage.of(topic, msg, MqttQoS.AT_LEAST_ONCE))
        }
    }

    fun sendHomeAssistant(device: Device) {
        log.info { "Sending homeAssistant discovery" }
        //there is no native support for an audio device via mqtt in HomeAssitant
        //while there are some plugins out there, nothing seems really well-supported
        //so just model it using existing types
        val topicPrefixState = "arylic/state/${device.name.lowercase()}"
        val topicPrefixCmd = "arylic/cmd/${device.name.lowercase()}"
        val model = device.host.split(".").first()
        log.info { "Host: ${device.host} -- model: $model" }
        val uuid = "arylic_${model}"

        //Thing mqtt:topic:arylic:Bureau "Arylic Bureau" (mqtt:broker:myBroker) @ "Arylic" {
        //  Channels:
        //    Type dimmer: volume       [stateTopic="arylic/state/bureau/volume",  commandTopic="arylic/cmd/bureau/volume", min="0", max="100"]
        //    Type switch: play         [stateTopic="arylic/state/bureau/playing", commandTopic="arylic/cmd/bureau/playpause", transformationPattern="DSL:truetoon.dsl"]
        //}

        val playPause = Switch.Builder()
            .withName("Play/Pause")
            .withIcon("mdi:play-pause")
            .withUniqueId(uuid + "_PlayPause")
            .withCommandTopic("${topicPrefixCmd}/playpause")
            .withStateTopic("${topicPrefixState}/playing")
            .withPayloadOn("true")
            .withPayloadOff("false")
            .build()

        val volume = Light.Builder()
            .withName("Volume")
            .withUniqueId(uuid + "_Volume")
            .withIcon("mdi:volume-high")
            .withCommandTopic("${topicPrefixCmd}/volume_on_off")
            .withStateTopic("${topicPrefixState}/playing")
            .withPayloadOn("true")
            .withPayloadOff("false")
            .withBrightnessScale(100)
            .withBrightnessStateTopic("${topicPrefixState}/volume")
            .withBrightnessCommandTopic("${topicPrefixCmd}/volume")
            .withOnCommandType("brightness")
            .build()


        val devices = listOf(volume, playPause).associateBy { it.uniqueId }

        val discovery = Discovery(
            Discovery.Device(
                device.host,
                "arylic",
                model,
                "MusicPlayer ${device.name}",
            ),
            Discovery.Origin(
                "arylic-mqtt"
            ),
            "not/used",
            devices
        )


        val msg = MqttMessage.of(
            "homeassistant/device/arylic-mqtt/${model}/config",
            discovery, MqttQoS.AT_LEAST_ONCE, true
        )
        emitter.send(msg)
    }

    @Incoming("cmd")
    fun consume(msg: MqttMessage<ByteArray>): CompletionStage<Void> {
        log.info { "Incoming message on: ${msg.topic}" }
        val matcher = PATTERN_TOPIC_CMD.matcher(msg.topic)
        if (!matcher.matches()) {
            log.info { "Can't process msg, topic must be in format $PATTERN_TOPIC_CMD" }
            return msg.ack()
        }
        val deviceName = matcher.group(1)
        val cmd = matcher.group(2)
        val device = controller.getConnection(deviceName)
        if (device == null) {
            log.info { "No device found with name $deviceName" }
            return msg.ack()
        }
        when (cmd.lowercase()) {
            "play" -> device.sendCommand(Command.Play)
            "pause" -> device.sendCommand(Command.Pause)
            "playpause" -> playpause(msg, device)
            "volume" -> volume(msg, device)
            "mute" -> device.sendCommand(Command.Mute(true))
            "unmute" -> device.sendCommand(Command.Mute(false))
            "device-info" -> device.sendCommand(Command.DeviceInfoCmd)
            "metadata" -> device.sendCommand(Command.PlaybackMetadata)
            "status" -> device.sendCommand(Command.PlaybackStatus)
            else -> log.info { "Unknown MQTT command: $cmd" }
        }
        return msg.ack()
    }

    private fun playpause(msg: MqttMessage<ByteArray>, device: ArylicConnection) {
        val payload = String(msg.payload)
        when (payload.uppercase()) {
            "PLAY", "ON" -> {
                log.info { "Sending play" }
                device.sendCommand(Command.Play)
            }

            "PAUSE", "OFF" -> {
                log.info { "Sending pause" }
                device.sendCommand(Command.Pause)
            }

            else -> device.sendCommand(Command.PlayPause)
        }
    }

    private fun volume(msg: MqttMessage<ByteArray>, device: ArylicConnection) {
        val payload = String(msg.payload)
        val volume = payload.toIntOrNull()
        if (volume == null || volume < 0 || volume > 100) {
            log.info { "Invalid volume payload, ignoring: $payload" }
            return
        }
        device.sendCommand(Command.Volume(volume))
    }
}


@ConfigMapping(prefix = "mqtt")
interface MqttConfig {
    @WithDefault("true")
    fun enabled(): Boolean
}