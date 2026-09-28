# KanadeDX Oniimai 1.2.0

**Regular release · LSPosed API 102 module · Android 9+ / ARM64**

This release refreshes the Miuix settings and phone dashboard. It retains the
exact native profiles for **KanadeDX-260721.1649 (1.65)** and
**KanadeDX-260207.0635 (1.60)**. Other game builds are not verified.

## What's new

- Module home summary card with version/API badges and clearer setup guidance.
- Collapsing headers, back arrows, selection check marks and a three-step
  first-run flow.
- Settings organized into status panels, controls and footnotes; app language
  and license information grouped under **Connection → General**.
- Numeric input validation, keyboard Done actions and LED brightness presets.
- Dashboard live indicators, achievement rank/progress, judgment ratios,
  Fast/Late balance, larger song artwork and level badges.
- Clearer dark-mode sensor labels, widget selection/drag feedback and
  confirmation before discarding changed layouts.

The controller, NFC and native game-hook implementation is retained. No new
library dependency was added. [UI implementation summary](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.2.0/CHANGES-UI.md).

## Downloads and upgrade

| File | Contents |
| --- | --- |
| `Oniimai-Kanade-API102-1.2.0.apk` | Standalone module, version code 37. |
| `KanadeDX-Oniimai-Source-1.2.0.zip` | Matching tagged source, build/test scripts, documentation and notices. |
| `KanadeDX-Oniimai-Dependency-Sources-1.2.0.zip` | Pinned dependency sources, including graphics-path native code. |
| `SHA256SUMS-1.2.0.txt` | SHA-256 checksums for the three downloads. |

Install the module APK over the previous maintainer-signed module. Keep
**KanadeDX (`app.KanadeDX`)** selected in an LSPosed environment supporting
modern API 102 and native hooks, then fully stop and restart the game.
Existing settings and widget layouts remain in app data; do not clear that data
for this update. A locally signed build may require a different installation
path. [User guide](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.2.0/docs/USER_GUIDE.md).

The game, integrated game APKs, songs, artwork, firmware and signing keys are not
included. An APK with an embedded module does not automatically receive this
module update; integrated NPatch packages and patching instructions are not
part of this release.

## Validation and known limits

The 1.2.0 source passed **5,112 host checks**, plus **59 USB transport checks**
and **68 production phone-NFC handoff checks** against Android fakes. The
Korean/Simplified Chinese locale check, release build with lintVital, APK v2
signature, 16 KiB alignment and module-only distribution audit passed. Reading
the license texts from the built APK passed 17 checks. The signing certificate
matches the preceding module releases; dependency source hashes were verified
against the unchanged inventory.

- The revised UI has not received a fresh physical-device layout or gesture
  check. Compilation and host tests do not establish hardware behavior.
- Intermittent controller/USB disconnects remain unresolved. Reduced idle NFC
  polling is a mitigation, not a verified reset fix.
- Earlier rc5 phone-NFC recognition in a patched 1.60 host was confirmed on a
  rooted phone. Independent non-root operation and long-duration USB stability
  are not established. [Compatibility and prior observations](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.2.0/docs/COMPATIBILITY-1.65.md).

## License and source

GPL-3.0-only, with the adapted decoder's MPL-2.0 option and third-party notices
preserved. New code, tests and documentation were generated and modified with
OpenAI Codex; adapted code retains its attribution.

Provided without warranty, subject to the included licenses and applicable law.
This unofficial module grants no rights over the game, services, cards/accounts
or trademarks. [Disclaimer](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.2.0/DISCLAIMER.md)
· [Developer guide](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.2.0/docs/DEVELOPER_GUIDE.md).
