# User guide

Version 1.1.0-rc5 adds KanadeDX-260721.1649 (1.65) and preserves the 260207.0635 (1.60) profile. The published 1.0.0 APK supports only 1.60. See [compatibility and update instructions](COMPATIBILITY-1.65.md).

## First connection

Install and enable the module for `app.KanadeDX` in an API 102-capable LSPosed environment, then restart the game. Choose a language, display orientation and connection settings in first-run setup. Allow access to the USB controller.

`onii-mai Touch`, `Command`, `LED` and `NFC` identify different interfaces; IO4 uses HID. Check each role if selection is ambiguous. Settings persist in app data and can be lost when that data is cleared.

Use the draggable **Oniimai settings** shortcut inside the game for connection, display, button and LED settings. The module launcher's dashboard preview does not access real USB input or game memory.

## External display and dashboard

Connect an HDMI/DP monitor that Android exposes as a separate display, then select it and choose the rotation. A portrait game is rotated onto the external landscape surface. A wireless service exposing only a duplicate of the phone screen might not be selectable as a Presentation display.

Edit dashboard widgets to change placement and size. After switching back to the phone, use the draggable external-output shortcut or display settings to return to the monitor. Actual modes and performance depend on the device, cable, adapter and monitor.

## Cards

- Controller reader: check the NFC port and present a card during the game's scan window.
- Phone: turn on system NFC and **Connection → Phone NFC automatic reading**. Present a card to the phone antenna at the game start/card-wait screen. No external scanner screen opens.
- Both readers may be enabled; only the first valid completion for the active game scan is used.
- MIFARE Classic needs compatible phone hardware. The module does not convert arbitrary UIDs into Aime numbers or write cards.

[Phone NFC details](PHONE_NFC.md)

## License and source information

Open **App settings → Licenses and sources** in the module launcher, or **Connection → Licenses and sources** inside the game's controller settings. The Korean label is translated by the current UI language. The menu explains the GPL offer, AI involvement and third-party notices; each bundled license can be read offline. Back returns to the notice list, then to the original settings screen.

The source action opens the project repository in a browser. If you do not have private repository access, use the matching Source ZIP supplied with the APK or request it from the distributor. The source package includes build instructions. The module license does not grant rights over the game, card services or product names.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| No module shortcut | API 102 support, scope, process restart, exact supported game build |
| Command port selected as touch | Interface role/name, manual selection, hub changes |
| No controller input | USB permission, IO4 HID mode, data-capable cable, close settings |
| Only phone cards fail | System NFC, active game scan, antenna position, MIFARE Classic support. For an embedded NPatch module, update the embedded receiver and installed module together; see [phone NFC handoff](PHONE_NFC.md#npatch-result-handoff-correction-110-rc5). |
| A different card produces an error | Supported card format; never attach card numbers/dumps to issues |
| No external screen | Separate-display detection, HDMI/DP adapter, power and cable |
| Target build mismatch | Verify an authorized APK locally; never blindly change addresses |

NPatch integrated packages and patching steps are omitted from this distribution. [Reason](LEGAL_REVIEW.md#npatch)
