# KanadeDX 1.65 compatibility

**Module 1.1.0-rc1** adds the exact **KanadeDX-260721.1649 (1.65)** native build and retains **KanadeDX-260207.0635 (1.60)**. The previously published 1.0.0 APK does not support 1.65. This is a module update; it does not contain or replace the game.

## What changed

The earlier module recognized only the 1.60 ELF build ID. On 1.65 it correctly refused to install game hooks, so USB discovery could still work while game input, touch, lighting, startup-button activation and UI control were unavailable.

The module now selects one immutable build profile before installing any hooks. Each profile has its own 55 function/guard addresses, fingerprints and UI metadata location. Unknown builds are still refused; a matching package name or advertised version is insufficient.

The profile covers:

- Four core input hooks: touch log, frame update, held buttons and button edges.
- Eight button/FET LED hooks and the independent upper-speaker RGB path.
- Compact-mode migration and temporary external-output hiding of the game's control UI, with phone-mode restoration.
- Controller activation of the real initial character/start button.
- Statistics, jacket image, physical/phone card delivery and existing read-error flow.

USB packet parsing, NFC command timing and display rendering are unchanged by this update. The game addresses are separated in [target_160.h](../app/src/main/cpp/target_160.h) and [target_165.h](../app/src/main/cpp/target_165.h); [target_build.h](../app/src/main/cpp/target_build.h) selects the profile.

## Update

Install the supplied `Oniimai-Kanade-API102-1.1.0-rc1.apk`, keep the module scoped to `app.KanadeDX` in API 102-capable LSPosed, and fully stop/restart the game. Existing controller and dashboard preferences remain in place. Do not clear game data or reflash controller firmware for this update.

If building from source, follow [Build](BUILD.md). Use the same signing key as an installed module when updating it. The 1.0.0 GitHub release and its files remain historical 1.60 artifacts.

## Verification

- Both supplied APKs passed full APK/library/metadata hashes, ELF build ID and all 55 function/guard fingerprints per profile.
- Method signatures were matched, including the integer-ID jacket overload. Consumed input, LED, score, notes, UI, startup and Aime field layouts were compared. Unused fields that changed were not treated as shared assumptions.
- The three Aime interior guards were checked against disassembly of the polling/result/error-window branches, rather than guessed from neighboring method addresses.
- Android release compilation, APK v2 signing with the existing certificate and 16 KiB ZIP alignment passed.
- The full host suite passed **5,035 checks**, including **563 native state/profile checks**. These include accepting each exact build ID and rejecting truncated, extended and single-byte-mutated IDs. Locale and module-only distribution audits passed.
- On the connected rooted Xiaomi Android 16 phone, the installed game's native library hash matched the supplied 1.65 APK. Logs confirmed all core, LED, ceiling, statistics/album, UI, boot and Aime hook groups installed.
- A physical controller input activated the real startup button. The game's control UI subsequently entered external-output hidden state.
- The same running session logged three physical-card deliveries and one recoverable read failure progressing through the original error/entry flow. Card types and the visible error dialog were not independently identified in that observation.

Hook installation and host checks do not independently prove physical RGB colors, every touch zone, full-song timing, card/server behavior or non-root operation. Fresh 1.60 device testing was not performed during this update; its exact target verification and state tests remain part of the compatibility checks.

## Adding another game build

1. Work from an authorized local APK outside the repository. Record its hashes and ELF build ID; keep game binaries and metadata dumps private and untracked.
2. Match methods by full signature, not only by name or relative address. Check every consumed managed field and enum. An interior branch guard is not a method entry point.
3. Add a complete profile header and JSON manifest, then register it in `targetByBuildId`. Preserve previous profiles. If layouts differ, add explicit version-specific layout handling before enabling that adapter.
4. Verify the old and new APKs with `scripts/verify_target.py`, run the native profile-rejection and state tests, and build the module.
5. Check each feature on the device. Core status `15` means four input hooks installed, not that all optional adapters or physical devices passed.
