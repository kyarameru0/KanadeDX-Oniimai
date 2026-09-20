# KanadeDX Oniimai 1.0.0

**LSPosed API 102 module · Android 9+ / ARM64**

Tested only with **KanadeDX-260207.0635 (1.60)**. Other game versions have not been tested.

## Features

- Oniimai touch sensors, IO4 ring buttons and P1, with saved port selection.
- Game-driven button, reader and upper-speaker RGB lighting.
- Controller card reading and phone NFC directly inside the game.
- Rotated external monitor output, phone switching and configurable Miuix dashboard widgets.
- First-run setup, Korean/Simplified Chinese settings and an offline license viewer.

## Downloads and installation

| File | Contents |
| --- | --- |
| `Oniimai-Kanade-API102-1.0.0.apk` | Standalone module. |
| `KanadeDX-Oniimai-Source-1.0.0.zip` | Matching source, build/test scripts, documentation and notices. |
| `KanadeDX-Oniimai-Dependency-Sources-1.0.0.zip` | Pinned dependency sources, including graphics-path native code. |
| `SHA256SUMS-1.0.0.txt` | Download checksums. |

Install the module, enable it in an LSPosed environment supporting modern API 102 and native hooks, select `app.KanadeDX` as the scope, then fully restart the game. [User guide](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.0.0/docs/USER_GUIDE.md).

The game, integrated APKs, songs, artwork, firmware and signing keys are not included. NPatch patching instructions are not supplied; non-root validation is not claimed.

## Validation

The module passed **4,983 host checks**, 59 USB transport checks, actual APK notice readback, release compilation, signing/alignment and distribution checks. On Xiaomi Android 16, the 1.0.0 launcher opened GPL text and Android Back returned to the notice list. Remaining device checks and earlier functional observations are recorded in [Validation](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.0.0/docs/VALIDATION.md).

Start with the [developer guide](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.0.0/docs/DEVELOPER_GUIDE.md) for code excerpts, event flows, diagrams and test instructions.

## License, disclaimer and AI disclosure

GPL-3.0-only, with the decoder's MPL-2.0 option and third-party notices preserved. New code, tests and documentation were generated and modified with OpenAI Codex; adapted code retains its attribution.

Provided as is, without warranty. Liability is limited as permitted by applicable law and the included licenses; non-excludable rights and GPL/MPL rights remain intact. This is an unofficial project and grants no rights over the game, services, cards/accounts or trademarks. [Disclaimer](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.0.0/DISCLAIMER.md) · [License and distribution notes](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.0.0/docs/LEGAL_REVIEW.md).
