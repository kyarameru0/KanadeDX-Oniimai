# KanadeDX Oniimai 1.3.38

**Regular release · Standalone LSPosed API 102 module · Android 9+ / ARM64**

This release adds experimental phone-camera support and keeps the floating
Oniimai shortcut out of the way during songs. It retains the verified native
profiles for **KanadeDX-260207.0635 (1.60)** and **KanadeDX-260721.1649 (1.65)**.
Other game builds are not verified.

## What's new since 1.3.25

- **Gameplay shortcut:** hide the draggable Oniimai settings button while a song
  is playing and show it again on Results or when returning to the menu. The
  external-display dashboard keeps its existing Settings entry.
- **Labs → Game camera:** an experimental switch, OFF by default, enables the
  game's Take photo and Memorial photo settings after a successful camera check.
  Camera permission belongs to the game app. KanadeDX's own **SkipPhotoCamera**
  setting is respected; turn it OFF to use the feature.
- **Front/rear camera:** select a lens and restart the game. The adapter uses only
  the requested facing, without silently substituting a different camera.
- **Live preview:** read YUV frames from the existing Unity Camera2 session to
  work around the missing video-decoding shaders observed on the test device.
  This does not open a second camera.
- **Single-player framing:** center the white selection guide and crop; fill the
  right preview square without the idle shutter's black side bars. The preview,
  adjustment screen and final profile-photo save use the same selected region
  instead of the original two-player quarter-width offsets.
- **Horizontal mirroring:** save a separate preference for each lens (front ON,
  rear OFF by default). Preview, profile capture and newly captured song-result
  photos use that preference. Restart after changing it; existing saved photos
  are unchanged.
- **Translations:** camera controls and guidance are available in English,
  Korean and Simplified Chinese.

The game retains its photo consent, countdown, frame compositing, saving and
upload flow. This adapter does not add photo support to a server or fabricate a
successful upload. See [camera setup, implementation and limits](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.38/docs/FRONT_CAMERA.md)
and the [complete changelog](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.38/CHANGELOG.md).

## Downloads and upgrade

| File | Contents |
| --- | --- |
| `Oniimai-Kanade-API102-1.3.38.apk` | Standalone module, version code 79. |
| `KanadeDX-Oniimai-Source-1.3.38.zip` | Matching tagged source, build/test scripts, documentation and notices. |
| `KanadeDX-Oniimai-Dependency-Sources-1.3.38.zip` | Pinned dependency sources and native graphics-path sources. |
| `SHA256SUMS-1.3.38.txt` | Checksums for the three downloads. |

Install over the previous maintainer-signed module, keep **KanadeDX
(`app.KanadeDX`)** enabled in LSPosed with modern API 102 and native-hook support,
then fully stop and restart the game. Existing settings and widget layouts are
retained when app data is kept. Locally signed builds may use a different key.

Camera use is optional: enable **Oniimai → Labs → Game camera → Unlock game
photo settings**, grant Camera permission to the game, set KanadeDX's
**SkipPhotoCamera OFF**, select the lens/mirroring, and restart. Then choose the
game's own photo options and consent yourself.

The game, integrated NPatch/game APKs, songs, game artwork, firmware and signing
keys are not included. Updating this standalone module does not update a module
already embedded in a separately patched game APK.

## Validation and known limits

- **9,227 host checks passed** (8,303 Java and 924 native). These include camera
  permission/startup policy, rotation, pixel bounds, YUV conversion,
  centered/adjusted crops and asymmetric mirrored pixels, alongside the existing
  input, lighting, NFC, display and setup checks.
- Separate **82 USB-transport checks** and **68 phone-NFC Binder handoff checks**
  passed against host-side Android fixtures.
- Both original supported APKs passed all **72 native function fingerprint**
  checks. The locale checker passed for **496 messages** across three languages.
- The module APK is rebuilt from the release commit and checked for signing
  continuity, ZIP alignment, required notices and module-only packaging. Source
  archives and dependency hashes are verified before publishing.

On a Xiaomi Android 16 phone running **1.65 with LSPosed**, debugging confirmed
successful front-camera initialization and an active stream. The tester
confirmed live video, the centered guide and removal of the right-preview black
bars. These observations do **not** validate every camera path in this release.
The latest final-photo orientation and per-lens mirror controls, rear camera,
repeated capture, permission revocation, background/resume and camera use with
external output still need physical-device checks. **1.60 camera operation and
NPatch camera operation have not been verified on a device.**

No new claim is made about pre-connected monitors, hotplug, landscape launch,
rotation during external output or long-session USB stability. Intermittent USB
disconnects remain unverified as resolved. See the [display test matrix](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.38/docs/DISPLAY-STARTUP-TESTS.md)
and [prior compatibility observations](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.38/docs/COMPATIBILITY-1.65.md).

## License, source and AI disclosure

**GPL-3.0-only**, with the adapted decoder's MPL-2.0 option and third-party notices
preserved. This project contains AI-generated and AI-assisted code, tests and
documentation. OpenAI Codex was used for implementation, review and release
preparation with maintainer requirements and device feedback.

Provided without warranty, subject to the included licenses and applicable law.
This unofficial module grants no rights over the game, services, cards/accounts
or trademarks. [AI disclosure](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.38/AI_DISCLOSURE.md)
· [Disclaimer](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.38/DISCLAIMER.md)
· [Developer guide](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.3.38/docs/DEVELOPER_GUIDE.md).
