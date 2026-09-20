# License and distribution notes

Reviewed 2026-09-20 for version 1.0.0. This is not a legal opinion or a grant of rights over the game.

## What to keep with a release

| Component | License / action |
| --- | --- |
| Module | **GPL-3.0-only**. Provide the matching source, build instructions and license to APK recipients. A private repository link alone is insufficient if recipients cannot access it. |
| Adapted FeliCa decoder | Preserve the original attribution and **MPL-2.0** option. The modified file also has a GPL-3.0-only offer under MPL Section 3.3. Keep local modifications available under MPL as well. |
| Dependencies and references | Retain their notices and the versioned dependency sources. See [Third-party notices](../THIRD_PARTY_NOTICES.md), [Dependencies](DEPENDENCIES.md) and [Provenance](PROVENANCE.md). |

The original license files and source headers are authoritative. The app displays the packaged notices offline. This shorter document does not replace them or add restrictions to recipients' license rights.

<a id="license-decision"></a>
## Why GPL is retained

`Io4Output.java` cites `mai_pico/firmware/src/hid.c`; the consulted [firmware license](https://github.com/whowechina/mai_pico/blob/main/firmware/LICENSE) is GPL-3.0. Protocol facts alone do not prove protected code reuse, but this review has not established a sufficient basis for a permissive relicense. Under the maintainer's instruction, **GPL remains; no Apache conversion was applied**.

The separate MPL decoder treatment follows [Mozilla's combination guidance](https://www.mozilla.org/en-US/MPL/2.0/combining-mpl-and-gpl/). It addresses the decoder inside the module, not the proprietary game.

## Warranty and responsibility

The software is provided without warranty. Liability is limited to the extent permitted by applicable law and the included licenses. [GPL Sections 15–17](https://www.gnu.org/licenses/gpl.en.html#section15) supply the project's warranty and liability terms. The [Disclaimer](../DISCLAIMER.md) does not waive non-excludable rights or change the GPL grant.

The project is unofficial. Product names identify compatibility, not endorsement. The module license grants no rights over another party's game, service, card/account or trademarks.

## Remaining scope

The module makes same-process calls and reads internal game structures. GPL/proprietary-host compatibility and host modification permission remain unverified; the [FSF plug-in guidance](https://www.gnu.org/licenses/gpl-faq.en.html#GPLPluginsInNF) identifies this distinction. Neither publishing source nor adding a disclaimer settles those questions.

The release contains a standalone module, its source and dependency sources. Game APKs, songs, artwork, manufacturer software and firmware are excluded. Public-page findings are summarized in [Host references](HOST_PERMISSIONS.md); card data flow is in [Card behavior](CARD_RIGHTS.md). Publishing a release does not resolve the permission limits described above.

<a id="npatch"></a>
## NPatch scope

Compatibility paths remain in the code. This release supplies no integrated APK, loader, patching tool or patching instructions, and does not claim non-root device validation. This scope does not decide the legality of every local NPatch use.
