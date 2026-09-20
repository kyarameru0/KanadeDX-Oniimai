# Documentation map

**KanadeDX Oniimai** is an LSPosed API 102 module. Current source is **1.1.0-rc5**, adding **260721.1649 (1.65)** alongside **260207.0635 (1.60)**. The published 1.0.0 APK remains 1.60-only. [Compatibility and verification](COMPATIBILITY-1.65.md)

## Use the module

- [Quick start](../README.md#quick-start): install, select the LSPosed scope and start the game.
- [User guide](USER_GUIDE.md): USB, cards, LEDs, monitor controls and dashboard editing.
- [Validation](VALIDATION.md): what was checked on the host and on a physical device.

## Read or modify the code

Follow **Developer guide → Architecture → Build**. You can build and run host tests without a game APK, controller or phone.

| Document | What you will learn |
| --- | --- |
| [Developer guide](DEVELOPER_GUIDE.md) | Vocabulary, startup flow, worked input/NFC examples and how to make a small change. |
| [Implementation recipes](IMPLEMENTATION_RECIPES.md) | Complete example patches: a saved setting, a P1 tile and port recognition; apply, verify, undo and troubleshoot. |
| [Architecture](ARCHITECTURE.md) | Which component owns each operation, with diagrams and code excerpts. |
| [Build and test](BUILD.md) | Tool paths, commands, signing and verification. |
| [Controller protocol](CONTROLLER_PROTOCOL.md) | Port roles, sensor/button masks and lighting messages. |
| [USB card protocol](AIME_PROTOCOL.md) | Reader commands, RF timing and reconnection. |
| [Phone NFC](PHONE_NFC.md) | Foreground reading, permission service and result handoff. |
| [Contributing](../CONTRIBUTING.md) | What to include with a change and which checks to run. |

## References

| Document | Contents |
| --- | --- |
| [Third-party notices](../THIRD_PARTY_NOTICES.md) / [Provenance](PROVENANCE.md) | Actual adapted code, libraries, source revisions and controller references. |
| [Dependencies](DEPENDENCIES.md) | Versions, source artifacts and hashes. |
| [Security and privacy](../SECURITY.md) / [Card behavior](CARD_RIGHTS.md) | Data handling and useful bug-report boundaries. |
| [Disclaimer](../DISCLAIMER.md) / [License and distribution notes](LEGAL_REVIEW.md) / [Host references](HOST_PERMISSIONS.md) | Warranty, license summary and remaining permission limits. |
| [AI disclosure](../AI_DISCLOSURE.md) | How code and documentation were prepared. |
| [1.0.0 release notes](RELEASE-1.0.0.md) | Download contents and release validation. |

Documentation on `main` may be newer than a release. The release tag and Source ZIP identify that release's exact source snapshot; its checksum file identifies the downloadable assets.
