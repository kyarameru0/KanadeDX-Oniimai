# Cabinet TEST and SERVICE keys (1.3.4)

In **Onii settings > Buttons**, use **IO4 HID**. Launch the game and close the
Onii settings panel. The controller's IO4 TEST signal is now forwarded to the
game's own test-menu input. SERVICE is forwarded to the game's service input;
the current game screen decides whether that moves a selection or performs
another service action. There is no simulated screen tap or new game hook.

## Mapping

Byte offsets below include report ID 1. Reports must be exactly 64 bytes.

| Function | IO4 source | JNI button bit | Game JvsButtonID |
| --- | --- | --- | --- |
| Ring buttons 1-8 | Selected player bank, existing active-low mapping | 0-7 | 2-9 or 11-18 |
| P1 / START | Bank 0, bit 1 (byte 29, bit 1), active high | 8 | 10 or 19 |
| TEST | Bank 0, bit 9 (byte 30, bit 1), active high | 9 | 0 |
| SERVICE | Bank 0, bit 6 (byte 29, bit 6), active high | 10 | 1 |

TEST and SERVICE are cabinet-wide: selecting player 2 must not move them to
bank 1. They do not trigger the KanadeDX launch button. A short pulse survives
until the next game input frame; holding a switch retains its raw state without
inventing new press edges. Focus loss, disabled input, an open module settings
panel and stale input use the existing input suspension/release behavior.

Data path:

```text
IO4 interrupt report -> Io4Input.mask -> GameSession -> NativeBridge.submit
 -> InputState -> existing Jvs.GetRawState / GetTriggerOn hooks
 -> InputManager.UpdateAmInput -> game TEST / SERVICE behavior
```

## Evidence and limits

- The supplied Assistant `v1.0.1.exe` was checked. Its Oniimai v3 serial class
  reports `supportsKeyTest: false`; its configuration contains a keyboard/IO4
  selector but no side-key remapping fields. The separate older HID tester has
  a different byte layout and is not evidence for changing v3 IO4 offsets.
- The supplied `oniimai_v3_with_bootloader.bin` was checked, but the physical
  side-key mapping was **not recovered from that packaged binary**. No claim
  is made that its GPIO order or the third switch's purpose has been decoded.
- The IO4 system-bit definitions are corroborated by
  [segatools IO4 definitions](https://gitea.tendokyu.moe/TeamTofuShop/segatools/src/branch/master/common/board/io4.h)
  and the report layout in
  [mai_pico hid.c](https://github.com/whowechina/mai_pico/blob/main/firmware/src/hid.c).
  The mapping adapter in this module was written for its existing input-state
  contract; no firmware implementation was imported.
- In the locally supplied KanadeDX 1.60 and 1.65 metadata,
  `JvsButtonID.Test=0` and `Service=1`. Inspection of `InputManager.UpdateAmInput`
  in each binary confirms the system-input path calls the existing raw and
  trigger hooks. No target addresses or fingerprints were changed.
- The third physical switch remains **unassigned**. IO4 also has coin counters,
  but a protocol field alone does not establish which switch drives it. Do not
  turn a counter value or an unknown bit into a TEST/START press.

The supplied files match the previous local analysis copies by SHA-256:

| File | SHA-256 |
| --- | --- |
| v1.0.1.exe | `4822ca141dd0c93474fd403ae3e410bb8d5162cb53612c15ece63264424925b8` |
| oniimai_v3_with_bootloader.bin | `aa9bcef691bd2e26baee85489317f7b8d150aefc07d8d6e29e0d4febcca25743` |

## Validation

Host regression tests cover both player banks, TEST and SERVICE independently
and together, concurrent ring/P1 input, active-high release, invalid reports,
short presses, held-state/edge separation, input disable, stale USB release and
startup-button exclusion. The original game hook profiles remain unchanged.

**Physical controller operation is not yet tested for this build.** With input
enabled, close settings and press TEST after game startup. Check test-menu
entry, SERVICE operation, release/re-press, and normal ring/P1 operation after
leaving the menu. For diagnosis, logcat tag `OniimaiKanade` records only TEST /
SERVICE transitions as `IO4 cabinet TEST=... SERVICE=...`, not every USB packet.
USB keyboard mode remains unchanged; this addition is for IO4 HID mode.
