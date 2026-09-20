# KanadeDX 1.65 compatibility

**Module 1.1.0-rc5** adds the exact **KanadeDX-260721.1649 (1.65)** native build and retains **KanadeDX-260207.0635 (1.60)**. The previously published 1.0.0 APK does not support 1.65. This is a module update; it does not contain or replace the game.

## What changed

The earlier module recognized only the 1.60 ELF build ID. On 1.65 it correctly refused to install game hooks, so USB discovery could still work while game input, touch, lighting, startup-button activation and UI control were unavailable.

The module now selects one immutable build profile before installing any hooks. Each profile has its own 55 function/guard addresses, fingerprints and UI metadata location. Unknown builds are still refused; a matching package name or advertised version is insufficient.

The profile covers:

- Four core input hooks: touch log, frame update, held buttons and button edges.
- Eight button/FET LED hooks and the independent upper-speaker RGB path.
- Compact-mode migration and temporary external-output hiding of the game's control UI, with phone-mode restoration.
- Controller activation of the real initial character/start button.
- Statistics, jacket image, physical/phone card delivery and existing read-error flow.

The initial compatibility update did not change USB packet parsing, NFC command timing or display rendering. The game addresses are separated in [target_160.h](../app/src/main/cpp/target_160.h) and [target_165.h](../app/src/main/cpp/target_165.h); [target_build.h](../app/src/main/cpp/target_build.h) selects the profile. Subsequent ring and NFC changes are described below.

## Inner ring LED correction (rc2)

The game facade's `SetLedAllOff` clears its eight button lamps. The module had
also cleared the three independent white cabinet channels, cancelling the
inner ring's last brightness or fade when the game reset button lighting.
`LedState::buttonsOff` now mirrors the button-only operation. Explicit FET
commands still control cabinet lamps; backgrounding, stopping LED output and
turning off the ring option still send a hardware blackout.

The LED diagnostic report includes the last acknowledged Body / Circle / Side
brightness (0–255) and FET packet count. The first four acknowledged FET packets
per LED session are logged for troubleshooting; continuous animation is not
logged. A working color test confirms a physical output path, not that every
game scene has emitted a nonzero ring command.

The user confirmed that the inner ring now works during normal game operation.

## Intermittent USB disconnect investigation (rc3)

Two idle reproductions on rc2 had an unanswered NFC `DETECT` after a series of
valid no-card replies. USB detached approximately 5.5 and 5.2 seconds into the
request, before the module's 10-second timeout. Android also reported removal
and re-enumeration of USB hub nodes. The user reported uninterrupted monitor
output. These observations do not establish whether the reader firmware, USB
host, hub or power path caused the interruption.

rc3 reduces sustained empty-field traffic: after eight consecutive no-card
replies, RF remains off for at least 1,000 ms between completed polling cycles,
instead of 250 ms. Automatic reading remains enabled; card presence and leaving
the game scan restore the normal interval. In an idle scan, detection can take
roughly 1.6 seconds with the observed device timings. START/STOP sequencing,
300 ms RF settling, both card families and the timeout remain unchanged. This
is a mitigation to test, **not a verified fix for a controller or USB reset**.

A DETECT that stays unanswered for 1.5 seconds now records one early snapshot
without another USB command, cancel or fabricated game error. The snapshot
includes touch/HID packet ages, LED state and the last 24 NFC command-metadata
events. Separate last-stall, last-input-failure and last-detach records survive
game restarts and are included by **Copy diagnostics**. Each record is bounded
to 8,192 characters; no card numbers, UIDs, payloads, device serial numbers or
arbitrary exception text are included. A transport failure followed by the
detach broadcast no longer schedules recovery twice when cleanup already ran.

## Idle game entry correction (rc4)

After the initial rc3 observation, an idle RF `START` timeout was sent to the
game as a physical-card read error even though no card had been detected. The
existing error-to-entry adapter then correctly followed its input and advanced
the attract screen. The input to that adapter was wrong.

rc4 requires a validated positive `DETECT` reply in the **current polling
attempt** before forwarding a USB read failure to the game. A previous tap,
RF-on timeout, empty-field RF-off failure or unanswered DETECT is not evidence
of a new card. Detector status errors without a card list are treated as
unknown presence; they neither enter the game nor rearm held-card deduplication.

Failures without card evidence are diagnosed and recovered locally. After the
RF cooldown, automatic polling can resume in the same game scan; there is no
forced game transition or manual retry button. If a card was actually detected
and SELECT/authentication/read fails, the existing game error flow is retained.
Successful Aime/MIFARE reads and phone-NFC delivery keep their existing paths.

This corrects the false game transition. It does not claim to fix the separate
underlying controller/USB communication fault.

## Phone NFC service handoff correction (rc5)

A permissionless NPatch 1.60 host uses the installed module's NFC service. Its
receiver previously trusted the embedded APK's archive UID, which can differ
from the actual service sender and cause a valid card result to be discarded.
rc5 verifies the Binder sender's installed package instead. See [Phone NFC](PHONE_NFC.md)
for the transport distinction, regression coverage and embedded-update requirement.
The permissioned direct-reading path used by other APK configurations is unchanged.

## Version checks

There is no Android version-name or version-code allowlist. Compatibility is
selected using the actual native game library, independent of the APK's display
version. Unknown native builds are still blocked: disabling that check would
reuse unverified function addresses, managed field offsets and card state
branches. This release does not claim automatic support for arbitrary future
game builds.

## Update

Install the supplied `Oniimai-Kanade-API102-1.1.0-rc5.apk`, keep the module scoped to `app.KanadeDX` in API 102-capable LSPosed, and fully stop/restart the game. Existing controller and dashboard preferences remain in place. Do not clear game data or reflash controller firmware for this update.

If building from source, follow [Build](BUILD.md). Use the same signing key as an installed module when updating it. The 1.0.0 GitHub release and its files remain historical 1.60 artifacts.

## Verification

- Both supplied APKs passed full APK/library/metadata hashes, ELF build ID and all 55 function/guard fingerprints per profile.
- Method signatures were matched, including the integer-ID jacket overload. Consumed input, LED, score, notes, UI, startup and Aime field layouts were compared. Unused fields that changed were not treated as shared assumptions.
- The three Aime interior guards were checked against disassembly of the polling/result/error-window branches, rather than guessed from neighboring method addresses.
- Android release compilation, APK v2 signing with the existing certificate and 16 KiB ZIP alignment passed.
- The rc4 host suite passed **5,112 checks**, including **572 native state/profile checks**, **948 NFC channel checks** and **156 NFC worker/lifecycle checks**. These cover unknown/empty/positive card evidence, RF failures before and after confirmed detection, same-scan idle recovery, retained real-card error feedback, held-card deduplication, bounded idle polling and private command snapshots. Locale and module-only distribution audits passed.
- rc5 passed **111 focused NFC checks**: 68 production Binder receiver checks and 43 card-format/lifecycle checks. The archive-UID regression fails with the previous receiver and passes with rc5. Release compilation, signing, 16 KiB alignment, localization and module-only distribution checks passed.
- On the connected rooted Xiaomi Android 16 phone, the installed game's native library hash matched the supplied 1.65 APK. Logs confirmed all core, LED, ceiling, statistics/album, UI, boot and Aime hook groups installed.
- A physical controller input activated the real startup button. The game's control UI subsequently entered external-output hidden state.
- The same running session logged three physical-card deliveries and one recoverable read failure progressing through the original error/entry flow. Card types and the visible error dialog were not independently identified in that observation.
- rc5 was installed in the existing NPatch 1.60 host on the rooted Xiaomi Android 16 phone, with the same signing identity and no data reset. Logs showed the permissionless `bound-service` path and two `Phone card delivered in game` events; the tester confirmed successful recognition. This is not a fresh physical 1.65 or independently unrooted-device test.
- rc4 installed over rc3 with settings preserved. Logs confirmed the 1.65 native hook groups and NFC connection; a controller input activated the real startup button. The false-entry cases above are covered by simulated transport tests. Fresh physical-card behavior and long-duration USB fault prevention are not established by this revision's installation check.

Hook installation and host checks do not independently prove physical RGB colors, every touch zone, full-song timing, card/server behavior or non-root operation. Fresh 1.60 device testing was not performed during this update; its exact target verification and state tests remain part of the compatibility checks.

## Adding another game build

1. Work from an authorized local APK outside the repository. Record its hashes and ELF build ID; keep game binaries and metadata dumps private and untracked.
2. Match methods by full signature, not only by name or relative address. Check every consumed managed field and enum. An interior branch guard is not a method entry point.
3. Add a complete profile header and JSON manifest, then register it in `targetByBuildId`. Preserve previous profiles. If layouts differ, add explicit version-specific layout handling before enabling that adapter.
4. Verify the old and new APKs with `scripts/verify_target.py`, run the native profile-rejection and state tests, and build the module.
5. Check each feature on the device. Core status `15` means four input hooks installed, not that all optional adapters or physical devices passed.
