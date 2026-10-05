# UI preview gallery

Versioned desktop UI renders in light and dark themes. These are sample-data
renders, not Android device screenshots; each section identifies its source
revision where it differs from the current release. The original 1.2.1 gallery
was rendered from `1.2.0-ui`, so some badges show 1.2.0. Setup and About previews
come from later 1.3.x revisions and are not exact 1.3.25 screenshots. Device fonts,
insets, scrolling and real content can differ. The original dashboard sensor
illustration is a placeholder; Android retains its live `SensorBoard` view.

The original 1.2.1 images are unchanged from the supplied `preview/after` set. No game
artwork or real card/account data is shown. The corresponding shared layouts are
in [OniScreens.kt](../app/src/main/java/io/oniimai/kanade/OniScreens.kt) and
[OniTheme.kt](../app/src/main/java/io/oniimai/kanade/OniTheme.kt).

[Current release notes](RELEASE-1.3.25.md) · [Implementation notes](../CHANGES-UI.md)

## Module home

| Light | Dark |
| --- | --- |
| <img src="assets/ui-1.2.1/01-home.png" alt="Module home, light theme" width="300"> | <img src="assets/ui-1.2.1/01-home-dark.png" alt="Module home, dark theme" width="300"> |

## Connection settings

| Light | Dark |
| --- | --- |
| <img src="assets/ui-1.2.1/02-settings-connection.png" alt="Connection settings, light theme" width="300"> | <img src="assets/ui-1.2.1/02-settings-connection-dark.png" alt="Connection settings, dark theme" width="300"> |

## LED settings

| Light | Dark |
| --- | --- |
| <img src="assets/ui-1.2.1/03-settings-led.png" alt="LED settings, light theme" width="300"> | <img src="assets/ui-1.2.1/03-settings-led-dark.png" alt="LED settings, dark theme" width="300"> |

## First-run setup

Redesigned in 1.3.9. Desktop Compose renders with scripted controller signals; on a device
every state comes from the real USB session.

| Welcome | Connection |
| --- | --- |
| <img src="assets/setup-1.3.9/01-welcome.png" alt="Setup welcome screen" width="300"> | <img src="assets/setup-1.3.9/02-connect.png" alt="Setup connection step, controller connected" width="300"> |

| Input check | Built-in screen |
| --- | --- |
| <img src="assets/setup-1.3.9/03-touch.png" alt="Setup input check, touch areas" width="300"> | <img src="assets/setup-1.3.9/04-screen.png" alt="Setup built-in screen orientation" width="300"> |

| Dark theme |
| --- |
| <img src="assets/setup-1.3.9/05-touch-dark.png" alt="Setup input check, dark theme" width="300"> |

Since 1.3.13 the controller's built-in screen shows each setup step too, and the ring buttons light with
it. Shown upright as they read on the controller (the screen itself is mounted a quarter turn, and the
picture turns with the rotation choice). The rim shows the colours the buttons' LEDs show.

| Connection | Input check (touch) | Lighting | Screen orientation |
| --- | --- | --- | --- |
| <img src="assets/setup-1.3.9/06-controller-connect.png" alt="Controller screen during setup: connected" width="200"> | <img src="assets/setup-1.3.9/07-controller-touch.png" alt="Controller screen during setup: touch areas under the sensors" width="200"> | <img src="assets/setup-1.3.9/08-controller-lighting.png" alt="Controller screen during setup: lighting preview" width="200"> | <img src="assets/setup-1.3.9/09-controller-screen.png" alt="Controller screen during setup: this side up" width="200"> |

## About

Added in 1.3.17 under Settings → General (and on the module launcher's home). Rendered with sample values;
on a device the phone, game version, module status and firmware come from the running session.

| Light | Dark |
| --- | --- |
| <img src="assets/about-1.3.17/about.png" alt="About page, light theme" width="300"> | <img src="assets/about-1.3.17/about-dark.png" alt="About page, dark theme" width="300"> |

## Single-choice list

| Light | Dark |
| --- | --- |
| <img src="assets/ui-1.2.1/05-choice.png" alt="Single-choice list, light theme" width="300"> | <img src="assets/ui-1.2.1/05-choice-dark.png" alt="Single-choice list, dark theme" width="300"> |

## Numeric input

| Light | Dark |
| --- | --- |
| <img src="assets/ui-1.2.1/06-number.png" alt="Numeric input, light theme" width="300"> | <img src="assets/ui-1.2.1/06-number-dark.png" alt="Numeric input, dark theme" width="300"> |

## LED brightness

| Light | Dark |
| --- | --- |
| <img src="assets/ui-1.2.1/07-brightness.png" alt="LED brightness, light theme" width="300"> | <img src="assets/ui-1.2.1/07-brightness-dark.png" alt="LED brightness, dark theme" width="300"> |

## Licenses and source

| Light | Dark |
| --- | --- |
| <img src="assets/ui-1.2.1/08-license.png" alt="Licenses and source, light theme" width="300"> | <img src="assets/ui-1.2.1/08-license-dark.png" alt="Licenses and source, dark theme" width="300"> |

## Dashboard with sample data

| Light | Dark |
| --- | --- |
| <img src="assets/ui-1.2.1/09-dashboard-live.png" alt="Dashboard with sample data, light theme" width="300"> | <img src="assets/ui-1.2.1/09-dashboard-live-dark.png" alt="Dashboard with sample data, dark theme" width="300"> |

## Idle dashboard

| Light | Dark |
| --- | --- |
| <img src="assets/ui-1.2.1/10-dashboard-idle.png" alt="Idle dashboard, light theme" width="300"> | <img src="assets/ui-1.2.1/10-dashboard-idle-dark.png" alt="Idle dashboard, dark theme" width="300"> |

## Widget editor

| Light | Dark |
| --- | --- |
| <img src="assets/ui-1.2.1/11-dashboard-edit.png" alt="Widget editor, light theme" width="300"> | <img src="assets/ui-1.2.1/11-dashboard-edit-dark.png" alt="Widget editor, dark theme" width="300"> |

## External welcome screen

Added in 1.3.5. This is the pre-game lobby on the cabinet monitor, in the rotated
1080x1920 buffer. It has a 1080x450 top panel and the main circle (centre 540,1380,
radius 540). The bezel hides everything else.

These are host renders of the real
[CabinetLobbyView.java](../app/src/main/java/io/oniimai/kanade/CabinetLobbyView.java),
drawn with Skia, sample status text and Windows fonts. They are not device captures.
On Android, it uses the game's fonts (or the system sans-serif fallback). These stills
are frames from the animation, with the clock fixed at 20:42. The ring mirrors the button
LEDs, so the loading frame catches the circling light and the ready frame catches the
wave and a ripple.

| Loading (English) | Ready (English) |
| --- | --- |
| <img src="assets/lobby-1.3.5/lobby-en-loading.png" alt="External welcome screen while the game loads, English" width="300"> | <img src="assets/lobby-1.3.5/lobby-en-ready.png" alt="External welcome screen ready to start, English" width="300"> |

| Loading (Korean) | Ready (Korean) |
| --- | --- |
| <img src="assets/lobby-1.3.5/lobby-ko-loading.png" alt="External welcome screen while the game loads, Korean" width="300"> | <img src="assets/lobby-1.3.5/lobby-ko-ready.png" alt="External welcome screen ready to start, Korean" width="300"> |
