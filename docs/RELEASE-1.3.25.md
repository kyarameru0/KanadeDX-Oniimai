# KanadeDX Oniimai 1.3.25

**Regular release · Standalone LSPosed API 102 module · Android 9+ / ARM64**

This release brings the supplied 1.3.x changes to GitHub: guided controller setup,
a cabinet welcome screen, display-startup safeguards and responsive dashboard
actions. It retains native profiles for **KanadeDX-260207.0635 (1.60)** and
**KanadeDX-260721.1649 (1.65)**. Other game builds are not verified.

## What's new since 1.2.1

- **Guided setup:** welcome, language, connection, screen direction, button/touch
  checks, lighting and completion. Connection status reflects the channels that
  actually opened. Detailed settings are available during setup; returning or
  finishing preserves their changed values. System Back follows the setup steps.
- **Installation-aware first run:** setup waits for the game's main screen after
  streaming-file installation. A translated in-app notice explains the wait.
  With no saved language, device languages select English, Korean or Simplified
  Chinese, with English as the fallback.
- **Cabinet screen:** before game launch, module status and a Start action occupy
  the cabinet's top panel and lower circle while KanadeDX's start screen stays on
  the phone. Setup also has a dedicated controller-screen view and lighting cues.
- **Display lifecycle:** output waits for advancing game updates and settled
  portrait configuration. Surface switching, retries, teardown and return to the
  phone have additional guards and a bounded diagnostic timeline. These are code
  changes, not a claim that every device's startup or rotation crash is resolved.
- **Controller recovery and controls:** improved handling of immediate USB read
  failures and missing devices, automatic port selection, clearer partial-link
  status, and cabinet test/service button mapping. See
  [cabinet keys](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.25/docs/CABINET_KEYS.md).
- **UI and lighting:** an About page, hardware-accelerated settings windows,
  revised transitions, reduced idle redraws, and fixes for settings refresh,
  rotation selection and the lighting-off state during setup.
- **Dashboard actions:** equal-height, centred buttons; width adapts to long
  labels, and narrow windows or large fonts switch to a vertical layout.

See the [complete changelog](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.25/CHANGELOG.md)
and [versioned UI preview gallery](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.25/docs/UI-PREVIEWS.md).
Preview images are desktop renders, not physical-device validation.

## Downloads and upgrade

| File | Contents |
| --- | --- |
| `Oniimai-Kanade-API102-1.3.25.apk` | Standalone module, version code 66. |
| `KanadeDX-Oniimai-Source-1.3.25.zip` | Matching tagged source, build/test scripts, documentation and notices. |
| `KanadeDX-Oniimai-Dependency-Sources-1.3.25.zip` | Pinned dependency sources and native graphics-path sources. |
| `SHA256SUMS-1.3.25.txt` | Checksums for the three downloads. |

Install the module over the previous maintainer-signed module. Keep
**KanadeDX (`app.KanadeDX`)** enabled in LSPosed with modern API 102 and native-hook
support, then fully stop and restart the game. Keep existing app data to retain
settings and widget layouts. Locally signed builds may use a different certificate.

The game, integrated NPatch/game APKs, songs, game artwork, firmware and signing
keys are not included. Updating this standalone module does not update a module
already embedded in a separately patched game APK.

## Validation and known limits

- The supplied 1.3.25 implementation passed **8,900 host checks** across 27 groups.
- Separate **82 production USB-transport checks** and **68 phone-NFC Binder
  handoff checks** passed against host-side Android fixtures.
- Both supported game APKs passed the repository's offline native-profile checks.
- Locale validation passed for **472 messages** in English, Korean and Simplified Chinese.
- Desktop Compose checks covered setup Back handling, callback cleanup, settings
  refresh and six rotation-choice regression scenarios. Dashboard actions were
  rendered in light/dark themes, normal/edit states and three width/font conditions.
- The release APK was rebuilt from the release commit; signature, ZIP alignment,
  required notices and module-only packaging were checked. Dependency source
  archives were verified against the unchanged inventory.

This release has **not** received fresh physical-device testing for pre-connected
monitors, hotplug, landscape launch, rotation during output, or long USB sessions.
Desktop rendering uses a different font environment from an Android device.
The display gates observe game update progress; they are not GPU completion fences.
Use the [device test matrix](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.25/docs/DISPLAY-STARTUP-TESTS.md)
when reporting display regressions.

Intermittent controller/USB disconnects remain unverified as resolved. Earlier
phone-NFC recognition in a patched 1.60 host was confirmed on a rooted phone;
independent non-root-device operation is not established by the current checks.
[Compatibility and prior observations](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.25/docs/COMPATIBILITY-1.65.md).

## License, source and AI disclosure

**GPL-3.0-only**, with the adapted decoder's MPL-2.0 option and third-party notices
preserved. This project contains AI-generated and AI-assisted code and
documentation. This release integrates the maintainer-supplied source archive;
OpenAI Codex was used for its review and release preparation.

Provided without warranty, subject to the included licenses and applicable law.
This unofficial module grants no rights over the game, services, cards/accounts
or trademarks. [AI disclosure](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.25/AI_DISCLOSURE.md)
· [Disclaimer](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.25/DISCLAIMER.md)
· [Developer guide](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.25/docs/DEVELOPER_GUIDE.md).
