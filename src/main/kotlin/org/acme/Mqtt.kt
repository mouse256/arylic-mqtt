package org.acme

import io.github.oshai.kotlinlogging.KotlinLogging
import io.smallrye.reactive.messaging.mqtt.MqttMessage
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Incoming
import java.util.concurrent.CompletionStage


@ApplicationScoped
class Mqtt {
    private val log = KotlinLogging.logger {}

    private lateinit var controller: Controller

    @Inject
    @Channel("state")
    private lateinit var emitter: Emitter<ReceiveCommand>

    fun setController(controller: Controller) {
        log.info { "Init MQTT" }
        this.controller = controller
    }

    fun handle(device: String, cmd: ReceiveCommand) {
        send(device, cmd)
    }

    fun send(device: String, msg: ReceiveCommand) {
        val topic = "arylic/state/${device}/${msg.name().lowercase()}"
        log.info { "Sending to topic: $topic" }
        return emitter.send(MqttMessage.of(topic, msg))
    }

    @Incoming("cmd")
    fun consume(msg: MqttMessage<ByteArray>): CompletionStage<Void> {
        log.info { "Incoming message on: ${msg.topic}" }
        return msg.ack()
    }
}