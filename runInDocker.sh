#!/bin/bash

./gradlew assemble && \
	docker build -f src/main/docker/Dockerfile.jvm -t quarkus/arylic-mqtt-jvm . && \
	docker run -i --rm \
	--net host \
	-e MQTT_HOST=192.168.1.215 \
	-e MQTT_PORT=1883 \
	-e MQTT_CLIENT_ID=arylic-mqtt-docker \
	-v /var/run/dbus:/var/run/dbus \
	-v /var/run/avahi-daemon/socket:/var/run/avahi-daemon/socket \
	-p 8080:8080 quarkus/arylic-mqtt-jvm
#	-e arylic_devices_0__ip=192.168.1.73 \
