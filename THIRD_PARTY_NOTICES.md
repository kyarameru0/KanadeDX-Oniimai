# Third-party notices

This is an independent interoperability module, not an official KanadeDX or Oniimai release. Third-party names identify compatibility and provenance, not endorsement.

## Project and adapted code

The project default is GPL-3.0-only; see [LICENSE](LICENSE). Module integration, dashboard and controller helpers were developed locally with Codex, including adaptations of earlier local Oniimai tools. Third-party implementations and libraries are identified below. No game mascot, sprite, patterned background or font binary is packaged.

`app/src/main/java/io/oniimai/kanade/AimeFelica.java` adapts the decoder and inverse tables of **zhicheng233/PN532-Aime-Reader**, `src/NfcAime.Dll/FeliCaDecryptor.cs`, commit **8feaf84860a17f10ebd12c3827a17cc7deaa01c3**, under **MPL-2.0**. Modifications: Java port, strict BCD validation, SEGA Aime issuer allowlist, no card-writing or IDm conversion. Its modified source and [MPL text](licenses/PN532-Aime-Reader-MPL-2.0.txt) are supplied.

For distribution as part of this GPL-3.0-only module, the modified decoder is additionally offered under GPL-3.0-only through MPL-2.0 Section 3.3; its MPL availability, including local modifications, is preserved. This does not change the upstream project's license. Recipients receive the covered source in the matching module Source ZIP alongside the APK; the repository also provides it at [kyarameru0/KanadeDX-Oniimai](https://github.com/kyarameru0/KanadeDX-Oniimai). Private repository access must not prevent an APK recipient from obtaining that source. [Mozilla combination guidelines](https://www.mozilla.org/en-US/MPL/2.0/combining-mpl-and-gpl/)

The Mai2Touch/Mai2LED protocol reference is Sucareto/Mai2Touch, MIT, **Copyright (c) 2026 Sucareto**. The complete permission and disclaimer notice is retained in [licenses/Mai2Touch-MIT.txt](licenses/Mai2Touch-MIT.txt).

## Runtime dependencies

Miuix, AndroidX/Compose, Kotlin, kotlinx-coroutines/serialization, Capsule, Guava ListenableFuture, JetBrains annotations and JSpecify are included as resolved build inputs. Their published licenses are Apache-2.0; exact artifact versions, binary/source hashes and URLs are in [dependency-inventory.json](dependency-inventory.json). Guava's license is inherited from its Maven parent. See [Apache-2.0](licenses/Apache-2.0.txt) and [dependency notices](licenses/DEPENDENCY_NOTICES.txt). Notices available in dependencies are retained during packaging.

AndroidX graphics-path includes native AOSP/Filament-origin code under Apache-2.0. Its pinned source tree is supplied with dependency sources. LLVM/compiler-runtime notices from NDK r27c are retained in [NDK-NOTICE.txt](licenses/NDK-NOTICE.txt) and packaged in the APK. Build tools/SDK/NDK themselves are external, separately licensed tools.

## Compile-only and reference material

libxposed API 102.0.0 is Apache-2.0 and compile-only. The framework supplies its runtime API and native hook/unhook functions; no libxposed API classes, LSPosed framework or independent hook engine are bundled. Native ABI declarations follow the official developer documentation.

Controller/card implementations consult Mai2Touch, mai_pico, Arduino-Aime-Reader, segatools and Arcade Docs as described in [Provenance](docs/PROVENANCE.md). Their entire firmware/tooling is not included and no rights over it are claimed.

The Oniimai Windows configuration application and controller firmware are distributed by the manufacturer at [onii.cc](https://onii.cc/): [assistant](https://onii.cc/assistant/), [firmware](https://onii.cc/firmware/). They were reference inputs for compatibility analysis. The manufacturer's acknowledgments of other projects do not themselves license its binaries or authorize this module. Those binaries and recovered proprietary implementation text are not included; see the [identity checks and limitations](docs/PROVENANCE.md#oniimai-manufacturer-references).

No original game APK, Unity/IL2CPP binaries, metadata, decompiled source, firmware, game artwork, songs, official logos or font binaries are provided. Game version facts and hashed compatibility signatures remain. No NPatch runtime, integrated game or packaging helper is distributed in this repository/release.

## Source and AI disclosure

The matching module source, build/test scripts and license texts accompany the module release. Official versioned dependency sources are also offered; see [Dependencies](docs/DEPENDENCIES.md). [AI disclosure](AI_DISCLOSURE.md) explains generated and adapted work. Existing authors' rights are not transferred by AI generation or by this notice.

This notice is not legal clearance for coupling GPL code to the nonfree game. See the unresolved issues in [Legal review](docs/LEGAL_REVIEW.md).

## Warranty and liability

The software is provided as is, without warranty, to the extent permitted by applicable law. Liability is limited only as permitted by applicable law and the included licenses. This does not exclude non-excludable liability, waive non-excludable rights or restrict recipients' GPL/MPL rights. Product names identify compatibility, not endorsement; the module license grants no rights over the game, services, cards/accounts or trademarks. See [Disclaimer](DISCLAIMER.md) and GPL Sections 15–17.
