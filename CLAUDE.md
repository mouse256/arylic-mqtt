# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Dev mode with live reload
./gradlew quarkusDev

# Build
./gradlew build

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "org.acme.SerdeTest"

# Run a single test method
./gradlew test --tests "org.acme.SerdeTest.encodeMute"

# Build Docker image
./gradlew build -Dquarkus.container-image.build=true

# Native build (requires GraalVM, or use container-build=true)
./gradlew build -Dquarkus.package.type=native
```

In dev mode, MQTT connector is disabled — the app won't try to connect to a broker. Set `%dev.arylic.devices[0].ip=...` in `application.properties` to point at a real device.

## Protocol reference

`PROTOCOL.md` documents all three device interfaces (TCP binary port 8899, HTTP API port 80, UPnP port 49152) with command tables reverse-engineered from the Android APK. Check it first when adding new device features.

## Architecture

All source code lives in `src/main/kotlin/org/acme/`. There is no sub-package structure.

### Data flow

```
Arylic device (TCP:8899)
  ↕ binary protocol (ArylicSerde)
ArylicConnection  (one per device, owns a blocking DataReader thread)
  ↕
Controller  (lifecycle, mDNS discovery, ping timer, HA integration)
  ↕
Mqtt  (SmallRye reactive messaging — inbound cmd/#, outbound state/*)
  ↕
ArylicResource / ArylicResourceToplevel  (JAX-RS REST, debug only)
```

### Key files

| File | Role |
|---|---|
| `Command.kt` | Sealed interface hierarchy for all device commands. `SentCommand` has `toPayload(): UByteArray`; `ReceiveCommand` has `name(): String`. Both interfaces can be implemented by the same class (e.g. `Mute`, `Volume`). |
| `ArylicSerde.kt` | Encodes `SentCommand → UByteArray` and decodes raw bytes → `ReceiveCommand`. Binary envelope: `[header 4B][length 4B][checksum 4B][reserved 8B][payload]`. Payload is ASCII: `PREFIX+ACTION+DATA\n` (e.g. `MCU+VOL+075\n`). |
| `UData.kt` | Cursor-based byte-stream wrapper used inside `ArylicSerde.decode()`. |
| `ArylicConnection.kt` | Manages one TCP socket. Background `DataReader` thread feeds raw bytes into `ArylicSerde`. Exposes `sendCommand()` (synchronized) and `expect<T>()` for one-shot response futures (used by REST endpoints). |
| `Controller.kt` | CDI `@ApplicationScoped` bean. On startup: starts Zeroconf mDNS listener (`_linkplay._tcp`), discovery reconnect timer (30 s), and ping timer (1 min). Manages the `connections` map. |
| `Mqtt.kt` | `@Incoming("cmd")` consumes `arylic/cmd/{device}/{action}` and dispatches to the right `SentCommand`. `@Outgoing("state")` publishes retained state messages to `arylic/state/{device}/{type}`. Also sends Home Assistant MQTT discovery payloads on connect. |
| `ArylicConfig.kt` | `@ConfigMapping(prefix = "arylic")` typed config. Devices list, timers, auto-discovery flag. |

### Threading model

Vert.x event loop + worker threads (Quarkus defaults) plus one `DataReader` daemon thread per connected device. All shared maps (`connections`, `listeners`, `discoveredDevices`) are guarded with `synchronized` blocks. Blocking socket operations run inside `vertx.executeBlocking()`.

### Protocol notes

- Binary header magic bytes: `0x18 0x96 0x18 0x20`
- Checksum = sum of all payload bytes (little-endian 32-bit)
- Metadata (`MEA+DAT`) payload is a JSON object with hex-encoded string values
- `AXX` prefix = device-originated responses; `MCU` prefix = controller/command messages
- Play/pause uses `-` separator instead of `+` (e.g. `MCU+PLY-PLA`)

### Internet stream playback

Stream playback uses the device's **HTTP API** (port 80), not the TCP binary protocol. `ArylicConnection.playStream(url)` hex-encodes the URL byte-by-byte and calls:
```
GET http://{host}/httpapi.asp?command=setPlayerCmd:play:{hex_url}
```

- MQTT command: publish the stream URL to `arylic/cmd/{device}/stream`
- REST: `POST /arylic/{device}/stream` with the URL as plain-text body

### Home Assistant integration

Volume is exposed as a HA `Light` entity (brightness maps to 0–100 volume). Play/pause is a `Switch`. Discovery messages are published to `homeassistant/...` on device connect.
