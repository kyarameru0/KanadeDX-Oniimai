# In-game phone NFC

Enable **Connection → Phone NFC automatic reading** (default on), turn on system NFC and present a card while the game is at its start/card-wait screen. No scanner Activity is opened for a card read. Controller USB reading can remain enabled.

## Supported data

Amusement IC Aime uses NFC-F Lite-S system 88B4, SEGA DFC0078 and the actual Aime access code from SPAD0, limited to issuer prefixes 500/501. MIFARE Aime-compatible cards use the existing key authentication and block1/block2 validation. Custom block1 data is allowed; the NBGIC marker and empty/nondecimal codes are rejected.

MIFARE Classic is optional Android hardware support. If absent, use the controller reader. Generic tag UIDs are not converted into access codes. These paths do not write card contents or firmware, and do not persist identities/access codes in preferences or logs.

Supported formats are not proof of an authentic issuer card or account ownership. Fixed authentication constants are included in the reader implementation. The module hands results to the game, whose own data handling is outside the reader's controls. [Card behavior and permission boundaries](CARD_RIGHTS.md)

## LSPosed transport

The original host lacks NFC permission. Its foreground Activity receives ReaderMode callbacks, then passes the Android Tag to the module's explicit bound service for permissioned tag I/O. Each Binder result is checked against the installed packages belonging to its Binder-supplied sending UID and the pending token. The sender must be the installed `io.oniimai.kanade` service. System NFC service permission enforcement is not changed.

Android package visibility is established through a grant to a zero-data provider. Updating/launching the module makes the grant; a first-connection `Theme.NoDisplay` registration Activity can perform it and immediately finish without creating a window. This is separate from reading and is not a scanner screen. The provider exposes no files or card/settings data.

A narrow hook catches SecurityException only in legacy Beam callback registration when the host lacks NFC permission. ReaderMode and actual tag operations retain their own checks. No system-server/NFC-process scope is used.

## Direct host transport

If the host has NFC permission, `PhoneNfcReader` uses `PhoneTagReader` directly on a worker. Transport is selected by the host's actual NFC permission, not by game version or whether NPatch is present. An NPatch host without NFC permission uses the same bound service described above and requires the module APK to remain installed separately. Neither path opens a scanner screen. Integrated game APKs and packaging recipes are excluded from the repository.

## NPatch result handoff correction (1.1.0-rc5)

An embedded NPatch module can receive archive `ApplicationInfo` from
`getApplicationInfo(modulePackage)`. Its UID may be 0 or -1 rather than the UID
of the separately installed module service. Earlier module code compared that
value to the Binder reply sender and silently discarded valid NFC results;
the pending read then timed out.

The receiver now uses `PackageManager.getPackagesForUid(message.sendingUid)`
to verify the installed service package. It does not accept a UID/package
claimed in the message payload, bypass system NFC permissions, or remove the
private callback, token, deadline and game-generation checks. Unknown senders
fail closed. Direct NFC reads are unchanged.

If NPatch embeds a module APK, updating only the separately installed module
does not replace the receiver inside the game. Use the updated module both as
the installed companion and in the embedded module configuration, then fully
restart the game. Keep the same app signing identity when updating to preserve
data; this change requires no game-data reset.

The regression test runs the production receiver with an archive UID of 0/-1
and a different installed service UID. The old receiver fails this case; rc5
passes 68 checks, including forged senders, stale tokens, cancellation,
expiration, real read errors and clearing received card bytes.

On the connected Xiaomi Android 16 phone, the existing NPatch 1.60 host was
updated with only the embedded module replaced; its manifest, permissions,
loader and game payloads stayed the same. The service path logged two successful
phone-card deliveries, and the tester confirmed in-game recognition. This phone
is rooted; a separate unrooted-device matrix and fresh 1.65 regression test were
not performed for this correction.

Reference: [Android PackageManager.getPackagesForUid](https://developer.android.com/reference/android/content/pm/PackageManager#getPackagesForUid(int)).

## Lifecycle

One read owns a token and game scan generation, with a six-second deadline. Settings, backgrounding, game-scan changes, or another reader completing cancel/obsolete the result. Only a valid completion reaches the game once. Tag connections close on cancellation. Read failures use the existing game error flow; no-card or explicit cancellation does not invent an error.

References: [NfcF](https://developer.android.com/reference/android/nfc/tech/NfcF), [MifareClassic](https://developer.android.com/reference/android/nfc/tech/MifareClassic), [package visibility](https://developer.android.com/training/package-visibility/automatic), Android16 official NfcService/NfcActivityManager sources. Protocol attribution is in [AIME_PROTOCOL.md](AIME_PROTOCOL.md) and [third-party notices](../THIRD_PARTY_NOTICES.md).
