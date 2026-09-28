# UI changes in 1.2.0

The settings and phone dashboard use the existing Kotlin, Jetpack Compose and
Miuix stack. Shared colors, spacing, typography and reusable components remain
in `OniTheme.kt`; no new library dependency is required.

## Settings and setup

- Module home summary card with a ring mark, version/API badges, colored row
  icons and numbered setup instructions.
- Collapsing large-title headers, back arrows, overscroll and haptic feedback.
- Three-step first-run progress with introductory panels. Rotation choices are
  disabled while external output is off.
- Live status panels, grouped controls and separate explanatory footnotes.
  App language and license information are under **Connection → General**.
- Check marks and selection semantics for single-choice lists, numeric range
  validation and keyboard Done actions.
- LED brightness presets and deduplicated integer brightness updates.

## Dashboard

- Live-data indicators, achievement rank/progress, judgment ratios and Fast/Late
  balance indicators.
- Larger song artwork with level badges and revised clock typography.
- Clearer sensor labels, judgment markers and dark-mode artwork placeholders.
- Widget selection outlines, consistent corner radii and drag emphasis.
  Save applies the layout; leaving a changed layout asks before discarding it.
  Unchanged layouts close directly.

## Implementation scope

The changes are in `OniTheme.kt`, `NativeUi.kt`, `NativeDashboard.kt`,
`LicenseUi.kt`, `DashboardView.java`, `GameUi.java` and `GameAssets.java` under
`app/src/main/java/io/oniimai/kanade/`. They integrate the maintainer-supplied UI
refinement into the working module while retaining existing control callbacks.
USB/NFC transport, native hooks and supported game-build profiles are unchanged
from the preceding source baseline.

See [release notes](docs/RELEASE-1.2.0.md) for downloads and validation limits.
Host checks and compilation do not establish on-device appearance or physical
controller behavior.
