# Validation

Current **1.1.0-rc4 / KanadeDX 1.65** observations are recorded in [Compatibility 1.65](COMPATIBILITY-1.65.md). The following is the historical **1.0.0** validation record.

**Version 1.0.0 · tested only with KanadeDX-260207.0635 (1.60).** Other game builds have not been tested.

## Automated checks

| Check | Result |
| --- | --- |
| Host suite | **4,983 checks passed**, including 511 native state/fingerprint checks and nine notice-reader checks. |
| USB transport suite | **59 checks passed** for ownership, cancellation and interface state using fakes. |
| Actual APK notice reader | Eight bundled texts loaded, plus nine failure/ownership checks: **17 checks passed**. |
| Locale audit | 156 catalogue entries and 258 inline bilingual pairs; no uncovered Korean UI literals. |
| Build/package | Release compilation, lintVital, APK v2 signature, existing signing-certificate continuity and 16 KiB ZIP alignment passed. |
| Distribution | Source links, configured credential patterns, module-only APK markers and exact packaged-notice matching passed. |
| Supported target | The local target verifier matched the APK/library/metadata hashes, ELF build ID and all 55 RVA/fingerprint mappings. |
| Dependency sources | 60 versioned source JARs and the pinned graphics-path native source archive were checksum-verified. |
| README themes | Light/dark image selection and live theme switching checked in Chromium; both banners visually inspected. |

The release's `SHA256SUMS-1.0.0.txt` identifies the exact downloadable assets. The source ZIP and `v1.0.0` tag identify the corresponding source tree. Commands are documented in [Build](BUILD.md).

## Device observations

On a rooted Xiaomi Android 16 phone, the installed 1.0.0 launcher opened the notice list and GPL text. Android Back returned from the text to its parent list.

The earlier functional baseline, internal version 0.4.2-rc2, logged five successful in-game phone-NFC deliveries and two unsupported-card errors routed into the game flow, with the game Activity remaining focused. Those are baseline observations, not fresh final-release card tests; physical card types and the visible error popup were not independently confirmed in that session. Release cleanup changes documentation, packaged notices and build metadata, not the USB/card/game/display algorithms.

## Not verified by these checks

- Top Back, large-document scrolling and the in-game notice/input-guard path still need device verification.
- Non-root NPatch operation is not claimed as tested.
- Host tests do not certify every controller, card, firmware, monitor mode or long-duration frame performance.
- The documented build was previously exercised from an extracted source archive with no project cache or maintainer key, using the installed toolchain and shared dependency cache. This is not a clean-machine, offline or bit-for-bit reproducibility claim.
- Pattern scans are bounded checks, not proof that every secret or unattributed fragment is absent. No independent security audit or legal clearance is claimed.
