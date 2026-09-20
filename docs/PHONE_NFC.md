# In-game phone NFC

Enable **Connection → Phone NFC automatic reading** (default on), turn on system NFC and present a card while the game is at its start/card-wait screen. No scanner Activity is opened for a card read. Controller USB reading can remain enabled.

## Supported data

Amusement IC Aime uses NFC-F Lite-S system 88B4, SEGA DFC0078 and the actual Aime access code from SPAD0, limited to issuer prefixes 500/501. MIFARE Aime-compatible cards use the existing key authentication and block1/block2 validation. Custom block1 data is allowed; the NBGIC marker and empty/nondecimal codes are rejected.

MIFARE Classic is optional Android hardware support. If absent, use the controller reader. Generic tag UIDs are not converted into access codes. These paths do not write card contents or firmware, and do not persist identities/access codes in preferences or logs.

Supported formats are not proof of an authentic issuer card or account ownership. Fixed authentication constants are included in the reader implementation. The module hands results to the game, whose own data handling is outside the reader's controls. [Card behavior and permission boundaries](CARD_RIGHTS.md)

## LSPosed transport

The original host lacks NFC permission. Its foreground Activity receives ReaderMode callbacks, then passes the Android Tag to the module's explicit bound service for permissioned tag I/O. Each Binder result is checked against the expected UID and pending token. System NFC service permission enforcement is not changed.

Android package visibility is established through a grant to a zero-data provider. Updating/launching the module makes the grant; a first-connection `Theme.NoDisplay` registration Activity can perform it and immediately finish without creating a window. This is separate from reading and is not a scanner screen. The provider exposes no files or card/settings data.

A narrow hook catches SecurityException only in legacy Beam callback registration when the host lacks NFC permission. ReaderMode and actual tag operations retain their own checks. No system-server/NFC-process scope is used.

## Direct host transport

If the host has NFC permission, `PhoneNfcReader` uses `PhoneTagReader` directly on a worker. This supports the separately tested NPatch packaging design without a companion scanner screen. Integrated APKs and instructions are excluded from this release; physical non-root testing is not claimed.

## Lifecycle

One read owns a token and game scan generation, with a six-second deadline. Settings, backgrounding, game-scan changes, or another reader completing cancel/obsolete the result. Only a valid completion reaches the game once. Tag connections close on cancellation. Read failures use the existing game error flow; no-card or explicit cancellation does not invent an error.

References: [NfcF](https://developer.android.com/reference/android/nfc/tech/NfcF), [MifareClassic](https://developer.android.com/reference/android/nfc/tech/MifareClassic), [package visibility](https://developer.android.com/training/package-visibility/automatic), Android16 official NfcService/NfcActivityManager sources. Protocol attribution is in [AIME_PROTOCOL.md](AIME_PROTOCOL.md) and [third-party notices](../THIRD_PARTY_NOTICES.md).
