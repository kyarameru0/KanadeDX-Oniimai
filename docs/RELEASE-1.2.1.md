# KanadeDX Oniimai 1.2.1

**Regular release · LSPosed API 102 module · Android 9+ / ARM64**

Updated settings and dashboard UI, with a clearer game-launch action, visible
connection states and improved compact widgets. The native compatibility
profiles remain **KanadeDX-260721.1649 (1.65)** and **260207.0635 (1.60)**.

## UI previews

The images below are supplied desktop Compose renders with sample data, not
device screenshots. They were prepared from the `1.2.0-ui` source revision, so
some badges still show 1.2.0. This release packages that UI as **1.2.1**.
The sensor graphic is a preview placeholder; Android still uses the controller's
sensor layout. Device fonts, insets and scrolling can differ.

| Home · light | Home · dark |
| --- | --- |
| <img src="https://raw.githubusercontent.com/kyarameru0/KanadeDX-Oniimai/v1.2.1/docs/assets/ui-1.2.1/01-home.png" alt="Updated module home in light theme" width="300"> | <img src="https://raw.githubusercontent.com/kyarameru0/KanadeDX-Oniimai/v1.2.1/docs/assets/ui-1.2.1/01-home-dark.png" alt="Updated module home in dark theme" width="300"> |

| Dashboard · light | Dashboard · dark |
| --- | --- |
| <img src="https://raw.githubusercontent.com/kyarameru0/KanadeDX-Oniimai/v1.2.1/docs/assets/ui-1.2.1/09-dashboard-live.png" alt="Updated dashboard with sample data in light theme" width="300"> | <img src="https://raw.githubusercontent.com/kyarameru0/KanadeDX-Oniimai/v1.2.1/docs/assets/ui-1.2.1/09-dashboard-live-dark.png" alt="Updated dashboard with sample data in dark theme" width="300"> |

<details>
<summary>Connection and LED settings</summary>

| Connection states | LED controls |
| --- | --- |
| <img src="https://raw.githubusercontent.com/kyarameru0/KanadeDX-Oniimai/v1.2.1/docs/assets/ui-1.2.1/02-settings-connection.png" alt="Connection settings with status panels" width="300"> | <img src="https://raw.githubusercontent.com/kyarameru0/KanadeDX-Oniimai/v1.2.1/docs/assets/ui-1.2.1/03-settings-led.png" alt="LED settings with a status panel" width="300"> |

</details>

[All 11 screens in light and dark themes](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.2.1/docs/UI-PREVIEWS.md).

## Changes

- A dedicated **Open KanadeDX** button in the home summary card.
- Tinted controller, Aime, phone NFC, external-output and LED status panels at
  the top of settings groups, separate from explanatory notes.
- Compact Edit/Save header actions, more room for song titles and artists,
  status dots and shorter text in narrow device widgets, and a larger wide clock.
- Shared screen content in `OniScreens.kt`; Android dialogs, preferences,
  bitmap conversion and controller callbacks stay in their existing adapters.
- No new runtime dependencies or changes to native game hooks and USB/NFC
  transport. [Implementation notes](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.2.1/CHANGES-UI.md).

## Downloads and upgrade

| File | Contents |
| --- | --- |
| `Oniimai-Kanade-API102-1.2.1.apk` | Standalone module, version code 38. |
| `KanadeDX-Oniimai-Source-1.2.1.zip` | Matching tagged source, documentation, previews and build/test scripts. |
| `KanadeDX-Oniimai-Dependency-Sources-1.2.1.zip` | Pinned dependency sources and graphics-path native source. |
| `SHA256SUMS-1.2.1.txt` | SHA-256 checksums for those three files. |

Install over the preceding maintainer-signed module, retain the `app.KanadeDX`
LSPosed scope, and fully stop/restart the game. Existing saved settings and
widget layouts remain in app data. An embedded NPatch module needs its own
update; integrated game APKs and patching instructions are not distributed here.
[User guide](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.2.1/docs/USER_GUIDE.md).

## Validation and limits

The source passed **5,112 host checks**, the Korean/Simplified Chinese locale
check, Android release compilation with lintVital, APK v2 signature verification,
16 KiB alignment and the module-only distribution audit. The existing signing
certificate is retained for update continuity.

The preview images do not establish physical USB/NFC behavior or Android touch
interaction. This UI revision has not received a fresh physical-device test.
Intermittent controller/USB disconnects remain unresolved, and independent
non-root operation is not established. [Compatibility and prior device observations](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.2.1/docs/COMPATIBILITY-1.65.md).

## License and source

GPL-3.0-only, with the adapted decoder's MPL-2.0 option and third-party notices
preserved. This revision integrates maintainer-supplied UI source and previews.
The project uses AI-assisted development; integration, checks and release
documentation were completed with OpenAI Codex.

The module distribution excludes the game, integrated game APKs, songs, game
artwork, controller firmware and signing keys. Provided without warranty under
the included licenses and applicable law. This unofficial project grants no
game, service, card/account or trademark rights.
[Disclaimer](https://github.com/kyarameru0/KanadeDX-Oniimai/blob/v1.2.1/DISCLAIMER.md).
