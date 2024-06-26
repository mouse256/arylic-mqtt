# arylic-mqtt

This program connects to the TCP API of [Arylic](https://www.arylic.com) devices and exposes them over MQTT.

The project is heavily a work-in-progress, not stable by any means.
It's also a learning exercise for me to learn kotlin, so the code quality on that
level may be sub-optimal

## Installation
Pre-built docker images are available on [github](https://github.com/mouse256/arylic-mqtt/pkgs/container/arylic-mqtt)

A sample systemd service is provided in [systemd-example.service](src/main/docker/systemd-example.service)

## Auto-discovery

Arylic devices can be auto-discovered, or statically configured.

Auto-discovery required additional permission to docker to be able to work:
* network mode = host to be able to send/receive discovery messages
* connection to dbus/avahi to resolve the discovered hostnames

## Usage
To run it locally, use the following command to start it

### With auto-discovery enabled
```
docker run \
	--net host \
	-e MQTT_HOST=192.168.1.152 \
	-e MQTT_PORT=1883 \
	-v /var/run/dbus:/var/run/dbus \
	-v /var/run/avahi-daemon/socket:/var/run/avahi-daemon/socket \
	ghcr.io/mouse256/arylic-mqtt:latest
```

### With auto-discovery disabled
```
docker run \
   --name arylic-mqtt \
   -e MQTT_HOST=192.168.1.152 \
   -e MQTT_PORT=1883 \
   -e ARYLIC_AUTO-DISCOVERY_ENABLED=false \
   -e ARYLIC_DEVICES_0__IP=192.168.1.73 \
   -p 8080:8080 \
  ghcr.io/mouse256/arylic-mqtt:latest
```

## Configuration
The project is based on [Quarkus](https://quarkus.io). Out-of-the box Quarkus configuration options can be used.

The following environment variables are important:

| Variable                      | Default   | Description                                                                                                                                                                                    |
|-------------------------------|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| QUARKUS_HTTP_PORT             | 8080      | Listening port for REST endpoint                                                                                                                                                               |
| MQTT_HOST                     | 127.0.0.1 | IP address of the MQTT server                                                                                                                                                                  |
| MQTT_HOST                     | 1883      | Port of the MQTT server                                                                                                                                                                        |
| MQTT_USERNAME                 | N/A       | MQTT username (optional)                                                                                                                                                                       |
| MQTT_PASSWORD                 | N/A       | MQTT password (optional)                                                                                                                                                                       |
| MQTT_SSL_...                  | N/A       | MQTT SSL options. See [smallrye mqtt configuration page](https://smallrye.io/smallrye-reactive-messaging/smallrye-reactive-messaging/3.4/mqtt/mqtt.html#_configuration_reference)  for options |
| ARYLIC_AUTO-DISCOVERY_ENABLED | true      | Enable/disable auto-discovery                                                                                                                                                                  |
| ARYLIC_DEVICES_0__IP          | N/A       | IP address of an Arylic device (if auto-discovery is disabled)                                                                                                                                 |

The ARYLIC_DEVICES variable can be repeated multiple times with a different index for multiple devices

All configuration options on the [smallrye mqtt configuration page](https://smallrye.io/smallrye-reactive-messaging/smallrye-reactive-messaging/3.4/mqtt/mqtt.html#_configuration_reference) can be used.
They need to be prefixed with `MP_MESSAGING_CONNECTOR_SMALLRYE_MQTT_` (eg `MP_MESSAGING_CONNECTOR_SMALLRYE_MQTT_SSL=true`)

## MQTT

MQTT is the main interface to interact with this tool. It can be used to get real-time updates of the status and can be used to control the device.

State is published on `arylic/state/#`

Commands are to be sent to `arylic/cmd/...`

## REST API
There is a (basic) REST api available for debugging purposes.

| Endpoint                     | Description               |
|------------------------------|---------------------------|
| /arylic                      | List of devices connected |
| /arylic/{device}/device-info | get device-info           |
| /arylic/{device}/mute        | mute                      |
| /arylic/{device}/unmute      | unmute                    |
| /arylic/{device}/metadata    | get metadata              |
| /arylic/{device}/status      | get playback status       |
| /arylic/{device}/play        | play                      |
| /arylic/{device}/playpause   | toggle play/pause         |
| /arylic/{device}/pause       | pause                     |

## OpenHab

The status published on MQTT can be used in various tool. The following example is on how to use it in OpenHab.

`things/arylic.things`
```
Thing mqtt:topic:arylic:Bureau "Arylic Bureau" (mqtt:broker:myBroker) @ "Arylic" {
  Channels:
    Type dimmer: volume       [stateTopic="arylic/state/bureau/volume",  commandTopic="arylic/cmd/bureau/volume", min="0", max="100"]
    Type switch: play         [stateTopic="arylic/state/bureau/playing", commandTopic="arylic/cmd/bureau/playpause", transformationPattern="DSL:truetoon.dsl"]
}
```

`transform/truetoon.dsl`
```
val output = Boolean.parseBoolean(input)
if (output) {
    "ON"
} else {
    "OFF"
}
```

`items/arylic.items`
```
Switch arylic_item_bureau_play       "Audio Bureau play"       {channel="mqtt:topic:arylic:Bureau:play"}
Dimmer arylic_item_bureau_volume     "Audio Bureau volume"     {channel="mqtt:topic:arylic:Bureau:volume"}
```

## Development

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./gradlew quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell script
./gradlew build
```
It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./gradlew build -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using: 
```shell script
./gradlew build -Dquarkus.package.type=native
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./gradlew build -Dquarkus.package.type=native -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/arylic-mqtt-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/gradle-tooling.

