# Source provenance

A scoped inventory of local implementation, adapted third-party code, protocol references and resolved dependencies. Reviewed 2026-09-20.

| Material | Source / revision | Relationship and license treatment |
| --- | --- | --- |
| Module glue, controller workers, hooks, display and UI | Local Oniimai development, written/modified with Codex | Project default GPL-3.0-only; human requirements and device feedback, not an assertion that AI holds copyright |
| Dashboard, widget layout, sensor geometry and earlier USB helpers | Earlier local Oniimai companion tools, subsequently adapted for KanadeDX | Local code reuse within this development work |
| Oniimai configuration application and controller firmware | Manufacturer's [onii.cc](https://onii.cc/), [assistant downloads](https://onii.cc/assistant/) and [firmware downloads](https://onii.cc/firmware/) | User-supplied reference binaries used for interoperability analysis. Neither binary nor recovered proprietary source is distributed. [Verification](#oniimai-manufacturer-references) |
| `AimeFelica.java` inverse substitution decoder | [PN532-Aime-Reader FeliCaDecryptor.cs](https://github.com/zhicheng233/PN532-Aime-Reader/blob/8feaf84860a17f10ebd12c3827a17cc7deaa01c3/src/NfcAime.Dll/FeliCaDecryptor.cs) | Adapted code/tables, MPL-2.0; additionally offered under GPL-3.0-only within this Larger Work using MPL Section 3.3. MPL availability is preserved. Java port with strict BCD and issuer checks; no card-write/IDm-conversion path |
| Touch/LED framing reference | [Sucareto/Mai2Touch](https://github.com/Sucareto/Mai2Touch) | Protocol reference; MIT notice including Sucareto copyright retained |
| IO4 input fields and ceiling RGB output | [whowechina/mai_pico hid.c](https://github.com/whowechina/mai_pico/blob/main/firmware/src/hid.c) | Firmware carries [GPL-3.0](https://github.com/whowechina/mai_pico/blob/main/firmware/LICENSE). `Io4Output.java` explicitly identifies this reference; the input mapping also follows the IO4 layout. Firmware is not bundled. Protocol facts and protected implementation must be distinguished; see the [license decision](LEGAL_REVIEW.md#license-decision). |
| NFC wire commands and examples | [Arduino-Aime-Reader](https://github.com/Sucareto/Arduino-Aime-Reader/tree/934484db83773477f77fee330ea79ca1c5d59e98) | Protocol/read-flow reference; its firmware is not included |
| NFC/card formats | [TeamTofuShop/segatools](https://gitea.tendokyu.moe/TeamTofuShop/segatools/src/commit/8a966b2454185699e8b7d256f4ab43384c274ba0), [Arcade Docs](https://sega.bsnk.me/allnet/amusement_ic/) | Format references; no emulator, server or game distribution. Fixed reader authentication constants and decoding tables are present in source; see [card boundaries](CARD_RIGHTS.md). |
| Android framework behavior | Official Android16 NFC service/activity-manager sources and [Android NFC docs](https://developer.android.com/reference/android/nfc/NfcAdapter) | API/permission research; framework code is not copied into the module |
| Java hook API | [libxposed API](https://github.com/libxposed/api), Maven 102.0.0 | Apache-2.0, compile-only; runtime supplied by framework |
| Native hook ABI | [LSPosed native-hook documentation](https://github.com/LSPosed/LSPosed/wiki/Native-Hook) | Minimal interface declarations; no hook engine bundled |
| Runtime UI/libraries | Miuix 0.6.1 and resolved AndroidX/Kotlin/Capsule/etc. | Exact artifacts and source hashes in [inventory](../dependency-inventory.json) |
| Native graphics dependency | [AndroidX graphics-path 1.0.1 source](https://android.googlesource.com/platform/frameworks/support/+/8a05a22af450d589ef911d772a001a49dcb05b71/graphics/graphics-path/) | Unmodified dependency; native code includes AOSP/Filament-origin Apache notices |
| Native toolchain runtime | Android NDK r27c / 27.2.12479018 | NDK notice retained, including runtime/LLVM notices; toolchain not bundled |
| Font selection | Installed Android/MiSans system files | Loads system fonts with fallback; no font binaries redistributed |
| Banner and README | Original SVG and prose; [crafting-effective-readmes](https://github.com/joshuadavidthomas/agent-skills/blob/516dee7a422b90937b2958d11c03694154ab9c09/crafting-effective-readmes/SKILL.md) (MIT), [Best-README-Template](https://github.com/othneildrew/Best-README-Template/tree/fc444eb7b04b2e4863f6080b1507a219e95103fa) (Unlicense), [awesome-readme](https://github.com/matiassingers/awesome-readme/tree/89e7efd54d7332ffc1876c1ceb7329dc4f117c59) | Documentation planning, structure and layout references; skill package and third-party images/source are not bundled |

## Target facts

`target-build.json`, `targets/*.json`, native RVAs and field offsets describe the supported user-supplied KanadeDX builds. Function compatibility checks store hashes, not raw original instruction bytes. The verifier reads a local authorized APK. No full IL2CPP binary, metadata dump, decompiled source or game asset is included. This minimizes distributed material but does not establish permission for reverse-derived interoperability facts or a legally separate work; see [review](LEGAL_REVIEW.md).

The host's public references are [kdx.nightcord.com.de](https://kdx.nightcord.com.de/) and [KanadeDX/Public releases](https://github.com/KanadeDX/Public/releases). These identify the host/distribution context, not an open-source game dependency or a modification license. [Checked permission evidence](HOST_PERMISSIONS.md)

## Oniimai manufacturer references

The [assistant page](https://onii.cc/assistant/) loads a [version manifest](https://munet-version-config-1251600285.cos.ap-shanghai.myqcloud.com/oniimai.json). The [firmware page](https://onii.cc/firmware/) links versioned firmware, with a separate [release history](https://onii.cc/firmware_release/).

| Reference input | Identity check on 2026-09-20 |
| --- | --- |
| `v1.0.1.exe` | 7,606,784 bytes; SHA-256 `4822ca141dd0c93474fd403ae3e410bb8d5162cb53612c15ece63264424925b8`. The supplied assistant matches the official manifest's version, digest and download filename. |
| `oniimai_v3_with_bootloader.bin` | 103,700 bytes; SHA-256 `aa9bcef691bd2e26baee85489317f7b8d150aefc07d8d6e29e0d4febcca25743`. The official `v0.3.5` download returned HTTP 403 during verification, so the supplied file's identity with that release is unverified. |

No open-source grant for these binaries was located on the reviewed manufacturer pages. Their protocol behavior informed compatibility work; the files are not bundled or relicensed by this project.

## Review limits

Local history and source comparisons are not a complete line-by-line authorship ledger. They cannot exclude every short, renamed, translated or structural adaptation in generated code. The MPL decoder and dependency licenses remain applicable. Any future license change must account for the actual rights holders of the affected code.
