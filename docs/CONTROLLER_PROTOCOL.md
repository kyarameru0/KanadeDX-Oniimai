# Oniimai controller protocol

This module implements Java transports and parsers for the Oniimai controller's separate USB roles. Desktop-assistant behavior and public interoperability projects informed the wire formats; no recovered application bundle or firmware is distributed. See [Provenance](PROVENANCE.md) and [Legal review](LEGAL_REVIEW.md).

## Command CDC protocol

Port product/function name: `onii-mai Command`. Parameters: 115200 baud, 8 data bits, no parity, 1 stop bit. Android implementation claims the CDC control/data interfaces using the CDC Union descriptor and asserts DTR/RTS.

Request, before escaping:

```text
53 command payload_length payload...
```

Response, before escaping:

```text
53 command status payload_length payload...
```

`0x53` is sync. Every occurrence of `0x53` or `0x7C` inside the body is prefixed with `0x7C`, without changing that byte. There is no checksum in this command framing. Status `0` means success. Length is one byte, excluding response command/status/length fields. A new unescaped sync resets the parser. Escaped last bytes are checked for frame completion too.

| Command | Meaning | Response payload |
|---|---|---|
| 00 | Read information | firmware uint32 LE; build uint32 LE; serial bytes 8..15 |
| 10 | Start touch debug | ACK |
| 11 | Stop touch debug | ACK |
| 12 | Unsolicited touch debug | 144 bytes |
| 13 | Restart touch sensor | ACK |
| 20 | Read configuration | 156 bytes |
| 21 | Apply configuration | ACK |
| 23 | Persist configuration | ACK |

All request operations are serialized. Debug `0x12` frames are dispatched separately. Replies for unrelated commands are ignored, not matched to a pending request. A timeout closes the channel to prevent a late same-command reply from being mistaken for the next operation.

### Touch debug

34 indices: `A1..A8 B1..B8 C1 C2 D1..D8 E1..E8`.

| Offset | Representation |
|---:|---|
| 0..7 | uint64 LE touch status, indices 0..33 |
| 8..75 | 34 uint16 LE raw readings |
| 76..143 | 34 uint16 LE baselines |

### Configuration

Each touch index occupies 4 bytes at `index * 4`: uint16 LE finger threshold, uint8 noise threshold, uint8 hysteresis.

| Offset | Field |
|---:|---|
| 136 | touch scan rate |
| 137 | button mode: 0 keyboard, nonzero IO4 in the Assistant |
| 138 | player: 0 P1, nonzero P2 in the Assistant |
| 139 | external LED brightness |
| 140 | front/button LED brightness |
| 141 | signed touch delay |
| 142 | not interpreted |
| 143..152 | 10-byte virtual card ID |
| 153..154 | not interpreted |
| 155 | config version |

This implementation retains the complete read buffer and patches only selected known fields. It rejects unknown payload lengths instead of reconstructing/padding them. UI limits from the Assistant are finger threshold 0..512, noise 0..255, hysteresis 0..128, brightness 0..255. These are UI constraints, not measurements of hardware electrical limits.

Apply sensitivity: GET → patch → SET → 200 ms → restart touch → 200 ms → start debug → GET and byte-for-byte compare. Brightness changes omit touch restart. Flash persistence is a separate explicit action after a fresh equality check, followed by SAVE and GET. Readback confirms reported live configuration; power-cycle persistence is untested.

## Legacy Touch CDC

`onii-mai Touch`, 9600 8N1. Start with `{HALT}{RSET}{STAT}`. Packets are `(` + seven binary bytes + `)`. Each byte contains five low-order state bits; the first 34 sequential bits map to the same zone order. The final unused bit is ignored. Stop with `{HALT}`. The app does not display raw values on this path.

## RGB LED compatibility path

Manufacturer documentation [onii.cc](https://onii.cc/) explicitly credits [Sucareto/Mai2Touch](https://github.com/Sucareto/Mai2Touch) for touch and LED protocol formats. The relevant public reference is [Mai2LED README](https://github.com/Sucareto/Mai2Touch/blob/main/Mai2LED/README.md) and [BD15070_4.h](https://github.com/Sucareto/Mai2Touch/blob/main/Mai2LED/BD15070_4.h).

This is an implementation of that documented wire format; support by the exact supplied BIN remains untested. `onii-mai LED`, 115200 8N1. Default destination `0x11`, source `0x01`.

```text
E0 destination source length command payload checksum
```

Length includes command/payload, excluding checksum. Checksum is the sum from destination through payload modulo 256. Body/checksum bytes `E0` or `D0` are escaped as `D0` followed by byte minus one.

The app enables responses (`7D`), queries board info (`F0`), uses single LED (`31`, index/R/G/B) or multi LED (`32`, start/end/skip/R/G/B/speed), then update (`3C`). Whole range end `0x20` is the reference's all-LED sentinel. Source, response command, checksum, status `1`, and report `1` are checked. Reference vectors are covered by tests.

## Buttons

The supplied Assistant's Oniimai class does not implement button test reports. Added paths:

1. Android hardware `KeyEvent` input while the user explicitly enables input monitoring. Default `W E D C X Z A Q`; per-button key learning allows remapping. The app does not change firmware keyboard mode.
2. Optional direct HID interrupt input using the SEGA IO4 report format shown in [mai_pico hid.c](https://github.com/whowechina/mai_pico/blob/main/firmware/src/hid.c). Accepted wire length is 64 bytes with report ID 1. The 16-bit LE button bank begins at byte 29 for P1 or 31 for P2. Main button bit order is `2,3,0,15,14,13,12,11`, active low. Physical correspondence on Oniimai is **unverified**. Invalid report lengths/IDs yield diagnostic HEX rather than guessed presses.

USB interfaces are read with Android's [USB Host API](https://developer.android.com/develop/connectivity/usb/host). No third-party USB driver library is included. Only CDC-ACM serial devices are supported in this version.


## Ceiling RGB (0.3.4)

The [manufacturer firmware history](https://onii.cc/firmware_release/) lists
“Fix Joystick protocol to correctly handle top led data” for v0.2.1. The
[mai_pico HID implementation](https://github.com/whowechina/mai_pico/blob/main/firmware/src/hid.c)
and [descriptor](https://github.com/whowechina/mai_pico/blob/main/firmware/src/usb_descriptors.h)
define a 64-byte output report (ID 0x10 + 63 bytes):

```
10 41 FC 00 R1 R2 G1 G2 B1 B2 00 ... 00
```

The adapter sets both player colors identically. It reuses the claimed IO4 HID
connection, uses interrupt OUT when present, and otherwise issues HID SET_REPORT
(request type 0x21, request 0x09, value 0x0210, index interface ID). One reader
thread drains all UsbRequest completions and separates input from output.
Output is bounded at 500 ms (interrupt) / 300 ms (control); an output timeout
stops further writes on that handle without closing button input. An explicit
ceiling reconnect action or replacement USB handle restores that path.

This is actual game Billboard RGB, not a copy of button colors. Exact KanadeDX
function fingerprints guard capture of IO.Jvs.SetPwmOutput and the game's RGB
test command. The JVS method is only a four-byte RET, so a validated single
branch to a nearby RX thunk is used instead of overwriting adjacent methods.
If the near allocation or page protection fails, only ceiling capture is marked
unavailable. No occupied mapping is overwritten. Game module code is not
persistently patched on disk.

The LED CDC's separate command 39 remains three white brightness channels
Body / Circle (Ext) / Side, with independent values and fades. It is not the
RGB ceiling report. Ring-button rotation never rotates cabinet or ceiling data.
