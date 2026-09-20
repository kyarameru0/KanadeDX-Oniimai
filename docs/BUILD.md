# Build and test

For a guided first change, use [Implementation recipes](IMPLEMENTATION_RECIPES.md). Those optional patches include focused checks and an installation workflow. The commands here build the full module from the repository root; adjust the example tool paths to your installation.

## Toolchain used for 1.0.0 and 1.1.0-rc1

- JDK 17 and Python 3.12 (standard-library scripts).
- Android SDK platform 36; build-tools 36.0.0.
- Android NDK **r27c / 27.2.12479018** in the tested environment.
- Gradle 9.1.0; Android Gradle Plugin 9.0.0; Compose compiler plugin 2.2.10.
- Modern libxposed API 102.0.0, compile-only.
- ARM64 output, minSdk 28, targetSdk 35.

Obtain SDK/NDK/JDK/Gradle separately under their vendors' terms. They are not in the repository. There is no Gradle wrapper; pass an unpacked Gradle directory. See [dependency inventory](../dependency-inventory.json) for resolved runtime versions, which can differ from requested versions through Gradle resolution.

## Windows PowerShell example

Use your own tool paths; the paths below are examples:

```powershell
$env:JAVA_HOME = 'C:/Tools/jdk-17'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
python scripts/build.py `
  --platform C:/Android/Sdk/platforms/android-36 `
  --build-tools C:/Android/Sdk/build-tools/36.0.0 `
  --ndk C:/Android/Sdk/ndk/27.2.12479018 `
  --java-home C:/Tools/jdk-17 `
  --gradle-home C:/Tools/gradle-9.1.0 `
  --build-dir work/module-build `
  --output work/Oniimai-Kanade-API102-1.1.0-rc1.apk
```

The builder compiles the native bridge with 16 KiB page alignment, stages it in `app/build/native-libs`, runs the release Gradle build, and verifies APK signing/alignment. It creates a local development signing key if one does not exist. The key is not shipped; the default development password is not a substitute for protecting that private key. The release uses the existing local development signing identity for update continuity and is not represented as an audited production signing setup.

Gradle's `prepareLicenseNotices` task packages `licenses/*.txt` and `THIRD_PARTY_NOTICES.md` as `META-INF/licenses/` resources. Update those source files directly; do not keep a second set of notices under `app/src/main/resources`. The release audit checks packaged notice content against the source.

The app's offline license viewer reads those same APK entries. After running the host suite, verify its reader against your actual APK with `java -cp work/tests io.oniimai.kanade.LicenseTextTest work/Oniimai-Kanade-API102-1.1.0-rc1.apk`. This checks the packaged text path without Android; also inspect both notice menus on a device before claiming UI validation.

Fresh local builds are not byte-identical to the maintainer's signed APK: signing identity and build metadata differ. A version tag records source, not a promise of bit-for-bit reproducibility. Pin dependency versions and compare the inventory before distributing your own build.

## Host tests

```powershell
python scripts/check_locales.py
python scripts/test.py --build-dir work/tests --ndk C:/Android/Sdk/ndk/27.2.12479018
python scripts/test_usb_transport.py --build-dir work/usb-tests
python scripts/audit_release.py --apk work/Oniimai-Kanade-API102-1.1.0-rc1.apk
```

`javac` and `java` must be on PATH. The NDK option also runs C++ state tests; without it only Java checks run. The USB transport test uses local fakes, not a physical controller. Unix paths can be supplied to the builder; the documented release environment is Windows. Do not interpret host tests as physical USB, NFC or display certification.

## Optional original-game verification

```powershell
python scripts/verify_target.py --apk C:/AuthorizedLocalCopy/KanadeDX-260207.0635.apk
python scripts/verify_target.py --apk C:/AuthorizedLocalCopy/KanadeDX-260721.1649.apk
```

This reads an authorized local file and selects its exact build profile, checking APK/library/metadata SHA-256, the ELF build ID, 55 RVA mappings and hashed function fingerprints per build. Unknown builds fail verification. It does not extract or modify the game. Building and host tests do not require this file. Never commit the APK or analysis outputs.

## Source and dependency archives

`python scripts/fetch_dependency_sources.py --output work/dependency-sources` downloads the official source artifacts listed in the inventory, verifies their hashes, and also fetches the pinned AndroidX graphics-path native source. This does not install or run them. The release provides these sources alongside the module source archive. These upstream source artifacts supplement the build instructions; they are not a self-contained copy of the entire Android SDK/NDK or AndroidX build environment.

NPatch builders and original-game packaging recipes are intentionally absent. See [distribution review](LEGAL_REVIEW.md#npatch).
