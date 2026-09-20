# Contributing

Read [Developer guide](docs/DEVELOPER_GUIDE.md), [Architecture](docs/ARCHITECTURE.md) and [Build](docs/BUILD.md) first.

## Where to change things

- Port roles and parsers: `PortSelection`, `Protocol`, `Io4Input`, `AimeProtocol`.
- USB lifecycle: keep blocking operations on workers, away from UI/game threads.
- UI: use Miuix components and the shared tokens in `OniTheme.kt`.
- Game versions: update `target-build.json`, `target_build.h` and the verifier together. Do not reuse unverified addresses or bypass failed compatibility checks.
- NFC: keep this implementation read-only. Do not log card identities or access codes.

## Submitting a change

Describe the problem, reproduction steps, resulting behavior and exact validation. State any device paths not tested. Record the original URL, immutable revision, license and modifications for external code. Disclose AI tools and their scope of use.

Only submit changes you have permission to provide. Do not commit game APKs, decompiled source, real card data or signing keys. Preserve existing GPL/MPL conditions instead of unilaterally changing them to a permissive license.

The maintainer requires retaining GPL-3.0 when third-party GPL-3.0 code is used. Check licenses in the relevant source subdirectory as well as the repository root. If protected reuse or licensing authority is uncertain, preserve the current license and document the evidence before proposing a change. See the [current license decision](docs/LEGAL_REVIEW.md#license-decision).

The modified `AimeFelica.java` is offered under both MPL-2.0 and GPL-3.0-only as part of the module's Larger Work. Keep modifications to that file available under both and preserve its upstream attribution. Do not paste GPL-only third-party code into it where you cannot also provide the result under MPL.

Host tests run without a game or controller. USB/display/NFC changes also need relevant device observations, or an explicit untested note. Before a release, run `python scripts/audit_release.py --apk <module.apk>` and inspect its bounded findings; this script is not a legal or security audit.
