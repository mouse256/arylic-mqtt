# Arylic / Linkplay Device Protocol

Notes from reverse-engineering the 4Stream Android APK (`4STREAM_3.1.20.230912_APKPure.apk`).

## Communication interfaces

The device exposes three interfaces:

| Interface | Port | Used for |
|---|---|---|
| TCP binary | 8899 | Real-time state, volume, play/pause, mute, metadata |
| HTTP API | 80 | Playback from URL, Bluetooth, Wi-Fi config, OTA |
| UPnP/DLNA | 49152 | Queue management, playlist playback |

## TCP binary protocol (port 8899)

Already implemented. See `ArylicSerde.kt` for encode/decode logic.

**Envelope:**
```
[Header: 4B = 0x18 0x96 0x18 0x20]
[Length: 4B little-endian]   ← byte count of payload
[Checksum: 4B little-endian] ← sum of payload bytes
[Reserved: 8B = 0x00 * 8]
[Payload: variable]
```

**Payload format:** `PREFIX+ACTION+DATA\n`

| Prefix | Meaning |
|---|---|
| `MCU` | Commands sent to device |
| `AXX` | Responses from device |

Known commands (from APK strings + implementation):

| Payload | Direction | Description |
|---|---|---|
| `MCU+VOL+NNN` | → | Set volume (000–100) |
| `AXX+VOL+NNN` | ← | Volume changed |
| `MCU+MUT+001` / `+000` | → | Mute / unmute |
| `AXX+MUT+001` / `+000` | ← | Mute status |
| `MCU+PLY-PLA` | → | Play |
| `MCU+PLY-PUS` | → | Pause |
| `MCU+PLY+PUS` | → | Toggle play/pause |
| `MCU+PLY+GET` | → | Request play status |
| `AXX+PLY+001` / `+000` | ← | Playing / paused |
| `MCU+DEV+GET` | → | Request device info |
| `AXX+DEV+INFname;type;...&` | ← | Device info (semicolon-delimited) |
| `MCU+MEA+GET` | → | Request playback metadata |
| `AXX+MEA+DAT{json}&` | ← | Metadata (JSON, strings hex-encoded) |
| `MCU+PINFGET` | → | Request full playback status |
| `AXX+PLY+INF{json}&` | ← | Full playback info (JSON) |
| `AXX+MEA+RDY` | ← | Device ready signal |

**EQ passthrough** (found in APK, not implemented):
```
MCU+PAS+EQ:bass:N
MCU+PAS+EQ:treble:N
MCU+PAS+EQ:mode:NAME
MCU+PAS+EQGet&
MCU+PAS+3D
MCU+PAS+BS   (bass boost?)
MCU+PAS+SL / SLG  (probably surround)
MCU+PAS+GET&
MCU+PAS+GetStatus&
MCU+PAS+B%02d, F%02d, M%02d, S%02d, T%02d, V%02d, W%02d  (band EQ values?)
```

**Other passthrough commands** (found in APK, not implemented):
```
MCU+PAS+HDMI+...   (HDMI mode)
MCU+PAS+LWD+DAF+... / LWD+TIM+...  (possibly LED/display)
MCU+PAS+{"fan_power":"ON"/"OFF"}  (fan control)
MCU+PAS+{"light_power":"ON"/"OFF"}
MCU+PAS+light-on/off/get/++/--
MCU+PAS+lightstyle-...
MCU+PAS+IOT
MCU+USB+GET & MCU+MMC+GET  (USB/SD card status)
MCU+EQ++GET
MCU+EQS+...
```

## HTTP API (port 80)

Base URL: `http://{device_ip}/httpapi.asp?command=COMMAND`

**Internet stream playback** (implemented in `ArylicConnection.playStream()`):
```
GET /httpapi.asp?command=setPlayerCmd:play:{hex_url}
```
The URL is hex-encoded: each byte becomes its 2-digit lowercase hex value.
Example: `http://example.com` → `687474703a2f2f6578616d706c652e636f6d`

Other HTTP API commands found in the APK (not implemented):

```
setPlayerCmd:switchmode:{mode}
wlanGetConnectState
connectbta2dpsynk:{mac}
disconnectbta2dpsynk:{mac}
startbtdiscovery:{timeout}
stopbtdiscovery
getbtdiscoveryresult
clearbtdiscoveryresult
getbthistory
delbthistory:{mac}
getbtstatus
getbtpairstatus
getbtPairDevStat
startgetbtPairDevStat
startbtserver:{port}
stopbtserver:{port}
getMvRomBurnPrecent
NotifyUpgradeType:firmware
EasyLinkResponseStop
```

## UPnP / DLNA (port 49152)

The app uses the Linkplay-proprietary `PlayQueue` UPnP service for playlist and queue management. Service descriptor: `/upnp/PlayQueueSCPD.xml`.

Control URL: `/upnp/control/PlayQueue1`

SOAP actions:
- `CreateQueue` — creates a new playback queue on the device
- `AppendTracksInQueue` — adds tracks to the queue
- `AppendTracksInQueueEx` — extended version (additional metadata)
- `PlayQueueWithIndex` — starts playback from a given queue index
- `GetAlarmQueue` / `SetAlarmQueue` / `DeleteAlarmQueue` — alarm/timer queues

Standard UPnP services also present:
- `/upnp/control/rendertransport1` — AVTransport (play/pause/seek)
- `/upnp/control/rendercontrol1` — RenderingControl (volume)
- `/upnp/control/renderconnmgr1` — ConnectionManager
- `/upnp/control/QPlay1` — QPlay (Tencent)
- `/upnp/control/LightManager1` — LED control

Track metadata uses DIDL-Lite XML. Audio streams use:
```
protocolInfo="http-get:*:audio/mpeg:DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;"
upnp:class = object.item.audioItem.musicTrack
             object.item.audioItem.audioBroadcast   (for radio streams)
```
