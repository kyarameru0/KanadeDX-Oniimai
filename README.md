<a id="top"></a>

<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/overview.svg" />
    <source media="(prefers-color-scheme: light)" srcset="docs/assets/overview-light.svg" />
    <img src="docs/assets/overview-light.svg" alt="Oniimai controller to KanadeDX: input, lights, NFC and external display" width="900" />
  </picture>
  <h1>KanadeDX Oniimai</h1>
  <p>An unofficial, independently developed LSPosed module connecting an Oniimai mini controller to KanadeDX on Android.</p>
  <p><strong>1.3.38</strong> · LSPosed module · Android 9+ · ARM64 · libxposed API 102</p>
  <p><a href="#quick-start">Install</a> · <a href="docs/USER_GUIDE.md">Use</a> · <a href="docs/DEVELOPER_GUIDE.md">Understand the code</a> · <a href="docs/BUILD.md">Build</a> · <a href="docs/README.md">All documentation</a></p>
</div>

**The current [1.3.38 release](https://github.com/kyarameru0/KanadeDX-Oniimai/releases/tag/v1.3.38) supports KanadeDX-260721.1649 (1.65) and KanadeDX-260207.0635 (1.60).** Compatibility uses these exact native builds, not a version-name guess. See [validation and update notes](docs/COMPATIBILITY-1.65.md). The historical **1.0.0 APK supports 1.60 only**.

**This project contains AI-generated and AI-assisted code, tests and documentation.** OpenAI Codex was used for development, review and release preparation; the maintainer also supplied revised source and device feedback. Third-party code retains its own authorship and licenses. [AI disclosure](AI_DISCLOSURE.md)

> **v1.3.38 · Standalone LSPosed module.** Experimental phone-camera support with front/rear selection, centered photo framing and per-lens mirroring. The floating settings shortcut hides during songs and returns on Results. [Camera setup and limits](docs/FRONT_CAMERA.md). [Release notes](docs/RELEASE-1.3.38.md). The release contains the module APK, module source and dependency sources. The game, songs, artwork, controller firmware and integrated game APKs are not included.

<details>
<summary>Contents</summary>

- [Features](#features)
- [Quick start](#quick-start)
- [How it works](#architecture)
- [Developer documentation](#development)
- [Validation](#validation)
- [License and credits](#license)

</details>

<a id="features"></a>
## Features

| Area | What the module provides |
| --- | --- |
| Controller input | Named USB-port selection, 34 touch sensors, eight ring buttons and P1, IO4 HID input |
| Lighting | Game-driven button/ring LEDs, reader LED and upper-speaker RGB |
| Cards | Controller Aime reader and **phone NFC directly inside the game**, with read failures routed into the game flow |
| External display | A portrait game rotated 90°/270° on a landscape monitor, with phone/external switching |
| Phone dashboard | Configurable song, score, judgment, sensor and device widgets; results retained until leaving the result screen |
| Settings | Kotlin · Jetpack Compose · Miuix UI, guided setup, grouped controls, English, Korean and Simplified Chinese app languages |
| Experimental camera | Opt-in Labs controls for the game's photo features, front/rear lens selection and saved horizontal mirroring; centered preview and capture |

Output refresh rate depends on the modes exposed by the phone, adapter, cable and monitor. Consistent frame delivery is not guaranteed on every setup. Phone MIFARE Classic reading requires compatible NFC hardware.

**Known limitation:** intermittent controller/USB disconnects remain unresolved. Reduced idle NFC polling and added diagnostics are mitigations, not a verified reset fix. See [the investigation and test limits](docs/COMPATIBILITY-1.65.md#intermittent-usb-disconnect-investigation-rc3).

<a id="quick-start"></a>
## Quick start

You need an Android 9+ ARM64 device, an LSPosed environment supporting **modern API 102 and native hooks**, your copy of the supported game, and an Oniimai controller with USB OTG.

1. Install `Oniimai-Kanade-API102-1.3.38.apk` from [Releases](https://github.com/kyarameru0/KanadeDX-Oniimai/releases/tag/v1.3.38).
2. Enable the module in LSPosed and select **KanadeDX (`app.KanadeDX`)** as its scope.
3. Fully stop and restart the game process.
4. Follow first-run setup: choose a language, connect the controller and grant USB permission when asked, pick the built-in screen orientation, then check buttons, touch and lighting. Touch and command ports have different roles.
5. Check **Oniimai settings → Devices** (the first tab) inside the game. For phone NFC, enable system NFC and present a card at the game's start/card-wait screen.

A local build uses your own signing key, so it may not install over the maintainer's APK. Back up settings before uninstalling an existing build. [Usage and troubleshooting](docs/USER_GUIDE.md)

**NPatch:** rc5 phone NFC handoff was confirmed in a patched 1.60 host on a rooted phone; independent non-root-device testing is not verified. See [phone NFC compatibility](docs/PHONE_NFC.md). Integrated APKs and patching instructions are not provided. [Scope](docs/LEGAL_REVIEW.md#npatch)

<a id="architecture"></a>
## How it works

```mermaid
flowchart LR
    USB[Oniimai USB] --> IO[Java input and reader workers]
    NFC[Phone NFC] --> Reader[Game ReaderMode]
    Reader --> Service[Module NFC service]
    Service --> IO
    IO --> JNI[JNI state bridge]
    JNI <--> Game[Verified KanadeDX hooks]
    Game --> LED[LED state to USB output]
    Game --> Stats[Score and judgment snapshots]
    Stats --> UI[Phone Miuix dashboard]
    Game --> Display[Unity Surface to external Presentation]
```

Blocking I/O stays on workers. Input snapshots cross JNI into game hooks. External output prefers a direct `SurfaceControl` path. Card results are accepted only while the request token and game scan generation still match. [Architecture, code excerpts and sequence diagrams](docs/ARCHITECTURE.md)

<a id="development"></a>
## Developer documentation

| Document | Start here for |
| --- | --- |
| [Developer guide](docs/DEVELOPER_GUIDE.md) | Start here: vocabulary, worked examples, code paths and a first-change workflow |
| [Implementation recipes](docs/IMPLEMENTATION_RECIPES.md) | Apply complete example patches for a setting, widget or USB alias; compile, verify and undo |
| [Architecture](docs/ARCHITECTURE.md) | Entry points, thread ownership, diagrams and code excerpts |
| [Build](docs/BUILD.md) | SDK/NDK/JDK/Gradle, signing, host tests and optional target verification |
| [Contributing](CONTRIBUTING.md) | Change boundaries, provenance, validation and AI disclosure |
| [Dependencies](docs/DEPENDENCIES.md) | Resolved artifacts, licenses and source hashes |
| [Source provenance](docs/PROVENANCE.md) | Local implementation, actual third-party code and controller references |
| [Controller protocol](docs/CONTROLLER_PROTOCOL.md) | Port roles, sensors and lighting transport |
| [Card protocol](docs/AIME_PROTOCOL.md) / [Phone NFC](docs/PHONE_NFC.md) | Read flow, lifecycle and format limits |
| [Experimental phone camera](docs/FRONT_CAMERA.md) | Opt-in setup, permission and frame flow, crop/mirror handling and device-test limits |
| [Validation](docs/VALIDATION.md) | Automated checks versus physical-device observations |

Most implementation code lives in `app/src/main/java/io/oniimai/kanade/` and `app/src/main/cpp/`. Building the module and running host tests do not require a game APK. The [documentation index](docs/README.md) groups the remaining references by task.

<a id="validation"></a>
## Validation

For the 1.3.38 checks and camera testing limits, see [release validation](docs/RELEASE-1.3.38.md#validation-and-known-limits). Live front-camera video and the centered guide/right-preview fixes were confirmed on a Xiaomi Android 16 phone running 1.65. The latest per-lens mirroring changes, rear camera and NPatch camera operation still need physical-device verification. This release does not establish a fix for every display startup or rotation crash.

On a rooted Xiaomi Android 16 phone, rc5 delivered phone-NFC results inside a patched 1.60 host, and the tester confirmed recognition. Separate 1.65 hook/input/card observations and the remaining hardware limits are recorded in [current validation](docs/COMPATIBILITY-1.65.md#verification). Independent non-root-device validation and long-duration USB stability are not established. [Historical 1.0.0 validation](docs/VALIDATION.md)

<a id="license"></a>
## License and credits

Licensed under [GPL-3.0-only](LICENSE). The adapted FeliCa decoder retains its MPL-2.0 option and is also offered under GPL-3.0-only through MPL Section 3.3. Dependency licenses and notices remain applicable. Provide matching source and notices with the APK. License texts are also accessible through the app's offline viewer. [Third-party notices](THIRD_PARTY_NOTICES.md) · [Provenance](docs/PROVENANCE.md)

Provided without warranty. Liability is limited as permitted by applicable law and the included licenses. See [Disclaimer](DISCLAIMER.md); it does not restrict your GPL rights.

This is an unofficial project, unaffiliated with the named game, hardware or framework developers. Names describe compatibility. The module license does not grant game, service, card/account or trademark rights. Host-combination and use permissions remain unverified; see the brief [distribution notes](docs/LEGAL_REVIEW.md).
