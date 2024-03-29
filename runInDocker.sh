#!/bin/bash

./gradlew assemble && \
	docker build -f src/main/docker/Dockerfile.jvm -t quarkus/arylic-mqtt-jvm . && \
	docker run -i --rm \
	-e MP_MESSAGING_CONNECTOR_SMALLRYE_MQTT_HOST=192.168.1.152 \
	-e MP_MESSAGING_CONNECTOR_SMALLRYE_MQTT_PORT=1883 \
	-e arylic_devices_0__ip=192.168.1.73 \
	-p 8080:8080 quarkus/arylic-mqtt-jvm
