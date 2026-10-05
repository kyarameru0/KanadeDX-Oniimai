# User guide

The current [1.2.1 release](https://github.com/kyarameru0/KanadeDX-Oniimai/releases/tag/v1.2.1) supports KanadeDX-260721.1649 (1.65) and preserves the 260207.0635 (1.60) profile. The historical 1.0.0 APK supports only 1.60. See [compatibility and update instructions](COMPATIBILITY-1.65.md).

## First connection

Install and enable the module for `app.KanadeDX` in an API 102-capable LSPosed environment, then restart the game. First-run setup opens on the KanadeDX main screen. While setup is open, the controller's built-in screen (and the controller drawn on the phone) shows each step itself (the 34 touch areas light under your fingers during the input check), or the game if it is already there, and the ring buttons light with it. After the welcome screen, choose a language (English, Korean or Simplified Chinese; the device language is preselected when supported). On the connection step, plug in the controller and allow USB access when Android asks; the step shows whether it is still looking, waiting for permission, connected, or failed. You can retry or connect later. Then pick the direction in which "This side up" reads upright on the built-in screen; it turns as soon as you choose, so the following steps read upright on the controller. Then check the eight buttons and 34 touch areas, and check the ring lighting: the controller's buttons light up with the step, and the status says whether they are connected. These steps also have **Advanced settings**, which opens the Settings page on the matching tab; changes there save at once. Opening setup again from Settings starts at the language step.

`onii-mai Touch`, `Command`, `LED` and `NFC` identify different interfaces; IO4 uses HID. Check each role if selection is ambiguous. Settings persist in app data and can be lost when that data is cleared.

Use the draggable **Oniimai settings** shortcut inside the game for connection, display, button and LED settings. The module launcher's dashboard preview does not access real USB input or game memory.

## External display and dashboard

Connect an HDMI/DP monitor that Android exposes as a separate display, then select it and choose the rotation. A portrait game is rotated onto the external landscape surface. A wireless service exposing only a duplicate of the phone screen might not be selectable as a Presentation display.

External output starts by itself whenever KanadeDX is launched with the monitor connected. Turning it off (the Display tab, or returning to the phone from the dashboard) lasts until the game is closed; the next launch turns it on again.

Before the game starts, the monitor shows the Oniimai welcome screen and the KanadeDX start screen stays on the phone. With LED output connected, the eight ring buttons follow that screen's light ring. One blue light circles while the game prepares. All buttons glow when Start is available, and they flash white when Start is accepted. The welcome screen stays up while KanadeDX shows its loading screen; as that loading ends, white fills the ring and the welcome screen fades out. The game then fades in on the monitor, the phone dashboard rises in, and the game's own lighting returns.

Edit dashboard widgets to change placement and size. Use Save to apply changes; leaving a changed layout asks before discarding it. After switching back to the phone, use the draggable external-output shortcut or display settings to return to the monitor. Actual modes and performance depend on the device, cable, adapter and monitor.

## Cards

- Controller reader: check the NFC port and present a card during the game's scan window.
- Phone: turn on system NFC and **Devices → Read cards with phone NFC**. Present a card to the phone antenna at the game start/card-wait screen. No external scanner screen opens.
- Both readers may be enabled; only the first valid completion for the active game scan is used.
- MIFARE Classic needs compatible phone hardware. The module does not convert arbitrary UIDs into Aime numbers or write cards.

[Phone NFC details](PHONE_NFC.md)

## License and source information

Open **App settings → Licenses and sources** in the module launcher, or **Devices → General → Licenses · sources** inside the game's controller settings. Labels follow the selected app language. The menu explains the GPL offer, AI involvement and third-party notices; each bundled license can be read offline. Back returns to the notice list, then to the original settings screen.

The source action opens the public project repository in a browser. The matching Source ZIP is also supplied with the APK. The source package includes build instructions. The module license does not grant rights over the game, card services or product names.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| No module shortcut | API 102 support, scope, process restart, exact supported game build |
| Command port selected as touch | Interface role/name, manual selection, hub changes |
| No controller input | USB permission, IO4 HID mode, data-capable cable, close settings |
| Controller disconnects or USB permission returns | An intermittent USB reset remains unresolved. Preserve **Copy diagnostics** after an occurrence; review it before sharing. Reduced idle polling is not a confirmed fix. See [the investigation](COMPATIBILITY-1.65.md#intermittent-usb-disconnect-investigation-rc3). |
| Only phone cards fail | System NFC, active game scan, antenna position, MIFARE Classic support. For an embedded NPatch module, update the embedded receiver and installed module together; see [phone NFC handoff](PHONE_NFC.md#npatch-result-handoff-correction-110-rc5). |
| A different card produces an error | Supported card format; never attach card numbers/dumps to issues |
| No external screen | Separate-display detection, HDMI/DP adapter, power and cable |
| Target build mismatch | Verify an authorized APK locally; never blindly change addresses |

NPatch integrated packages and patching steps are omitted from this distribution. [Reason](LEGAL_REVIEW.md#npatch)
