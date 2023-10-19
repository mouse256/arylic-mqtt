package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.handler.codec.mqtt.MqttQoS
import io.smallrye.reactive.messaging.mqtt.MqttMessage
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Incoming
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
            is Command.PlayStatus -> emitter.send(MqttMessage.of("${topicBase}/playing", msg.playing, MqttQoS.AT_LEAST_ONCE, true))
            else -> emitter.send(MqttMessage.of(topic, msg, MqttQoS.AT_LEAST_ONCE))
        }

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