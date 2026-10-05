# Changelog

## 1.3.25 — 2026-10-05

- The dashboard's bottom buttons keep one height and centred labels when a label is long (English "Use phone
  screen") or text is large. Each button took an equal share of the width, so a label that did not fit
  wrapped (even mid-word), making that button alone taller with its text off to one side. Labels now keep to
  one line: the buttons share the width equally when every label fits, and by what each needs when one
  does not; when they cannot all fit side by side they stack, one per line, as the platform's dialogs do.
  Buttons side by side always share one height, and a label that still has to wrap stays centred.

## 1.3.24 — 2026-10-05

- The phone's back (key or gesture) no longer closes setup halfway. It closed setup's whole window from any
  step; it now goes back the way the back arrows on screen do: out of the detailed settings, one step back,
  from done to lighting, and from the first step to the welcome screen. Only the welcome screen opened again
  from Settings closes setup with it; on first run setup stays. Other pages close with back as before.
- The dashboard's bottom buttons sit centred in their bar. The widget area kept 22 dp of empty space above
  the bar while the buttons had 8 dp below them, so they looked pushed down; now there is one card gap
  (10 dp) above and below them.

## 1.3.23 — 2026-10-05

- A rotation chosen in setup's detailed settings can no longer be undone by setup's own preview. While the
  detailed settings were open, a button press or touch made setup send its older choice to the controller's
  screen again, turning it back before setup had taken the new choice over, and finishing saved the old
  one. While they are open, setup's views now carry "rotation held": only a choice made there turns the
  screen (setup's page there shows the real direction), and setup takes it over when they close.

## 1.3.22 — 2026-10-05

- Settings, and setup's detailed settings, show changes on the open tab again. Since 1.3.20 the tab on screen
  read a snapshot that only another tab change replaced, so connection states and switches could stay on old
  values (and a stale switch could send the opposite request). The tab on screen now reads the newest groups;
  only a tab sliding out keeps its snapshot.
- A choice made in setup's detailed settings is no longer undone when setup finishes. Setup compared saved
  values, but the detailed settings show the live rotation preview and the running LED and Aime state, so
  choosing there the direction that was already saved went unnoticed. Setup now compares the values as the
  detailed settings show them, starting again whenever they open.
- Lighting turned off in setup stays off after finishing: the saved LED choice now reaches the ceiling RGB
  (IO4) as well as the ring board before setup's lighting lets go.
- With lighting turned off in setup, the body, ring and side (FET) lights go dark too, not only the eight
  buttons. The final LED values come from one function (LedOutput.compose), checked by host tests.

## 1.3.21 — 2026-10-05

- Fix pages leaving copies of themselves on screen as they closed. 1.3.20 slid each page's content inside
  its own full-screen, opaque window, so the strip it uncovered was never painted again and kept old frames.
  Pages now come and go as whole windows with the platform's window animation (Animation.Translucent: in
  from the right with a fade, out the same way, at the system's animation scale), which the system
  compositor moves in one piece. A closed page is gone at once, so the next one (setup from Settings)
  comes in while it leaves.
- Slim the phone dashboard's bottom bar ("Switch to phone", "Settings"): 42dp pill buttons with tight
  padding take about half the height they did, so the widgets above keep more of the screen.

## 1.3.20 — 2026-10-05

- Draw the module's pages on the GPU. KanadeDX's game activity turns hardware acceleration off
  (android:hardwareAccelerated="false" in both 1.60 and 1.65), and windows opened from it inherited that,
  so Settings, setup, About and every other page were drawn in software. Each page now asks for hardware
  acceleration itself, as the controller-screen windows already did. This was a main cause of the dropped
  frames in setup and About.
- Page transitions: every page (Settings, setup, About, licences, choices, number input, the dashboard
  preview) slides in from the right and back out to the right when closed, with system animations
  respected. A page on its way out no longer holds up the next one, so setup opened from Settings slides in
  while Settings slides away.
- Settings tabs slide a short way in the direction chosen over a cross-fade, each keeping its own content
  while it leaves. In setup, a step's words rise in as the old fade, its choices fade in, and the detailed
  settings page slides in over the step and back out.

## 1.3.19 — 2026-10-05

- Setup opened from Settings starts at the welcome screen again; there a back arrow closes it, and Back
  from the language step returns to the welcome screen.
- Setup's page now shows on the controller's display even when the game or its welcome screen is already
  there: it is put in front, the output underneath keeps running untouched (nothing is moved between
  screens), and an output that opens during setup puts the page back in front.
- Smoother setup and About. The controller's page and its mirror on the phone's drawing are recorded once
  and replayed; they are recorded again only when what setup shows changes, or while something on them
  moves (at most 30 times a second on the controller, 15 in the mirror), and a still page is not redrawn at
  all. About keeps its colour wash in a cached layer, reads the phone's and the game's details once instead
  of every second, and no longer redraws when nothing changed.

## 1.3.18 — 2026-10-05

- The About page's title is the module's full name, "Oniimai for KanadeDX", on one line in one gradient,
  as large as the screen's width allows. The KanadeDX card's module line now reads just "LSPosed API 102".

## 1.3.17 — 2026-10-05

- Add an About page under Settings → General, and on the module launcher's home. In the manner of the
  HyperOS and HyperCeiler About pages: a soft pink-to-blue wash behind the app's icon (drawn from the
  launcher icon), "Oniimai" in a gradient, and the version and build type; then a card for the phone (name,
  model, Android version, build number), one for KanadeDX (game version, module status, module), the
  firmware check and diagnostics copy, and the source code and licences. Values are read again every second
  while it is open; the launcher's page leaves out what only a running game knows.

## 1.3.16 — 2026-10-05

- Fix the phone staying on an old picture after "Return to phone" while the game runs on the monitor. When
  the game comes back, Unity puts its view back covered by a still of the phone's last frame and removes it
  only when told it has drawn again; that did not always happen, so the game ran unseen under the still
  until the monitor was turned on and off once more. Once the phone's surface is back and the game has
  drawn two more frames (two seconds at most), the module now takes the still away through Unity's own
  call and lets the game read the phone's size again. The display timeline records when it did.

## 1.3.15 — 2026-10-05

- Reorder setup to welcome, language, connection, screen direction, input check, lighting, done. Every page
  reads in the chosen language, and the input check and lighting read upright on the controller. Reopening
  setup from Settings starts at the language again.
- Show on the phone's controller drawing exactly what the controller's own screen shows: its top panel
  (heading, explanation, progress) through the top window and its circle (language, connection state,
  touch areas, button count, light ring, "this side up", done) through the round opening, drawn by the same
  code, following the drawing's angle and turning with the rotation choice.

## 1.3.14 — 2026-10-05

- Move the built-in screen's direction to right after the welcome screen, so every later step reads upright
  on the controller: welcome, screen direction, language, connection, input check, lighting, done. Reopening
  setup from Settings starts at the screen direction. That step reads the screen itself rather than the USB
  link, and asks for the direction in which "This side up" reads upright.
- The controller's welcome page leaves the circle to its words: the ring mark in the middle is removed.
- The controller's lighting page shows the step's colours as one band of light just inside the rim, each
  button's colour where that button is, blending into its neighbours with a soft glow, instead of eight dots.
- The lighting step no longer calls itself a preview: its status reads "Controller lights connected",
  "Lighting sync off", or that the LEDs are not connected yet, and the "Preview" tag is gone.
- Setup's steps are named in one place (SetupLights.WELCOME to DONE) for the phone, the controller's screen
  and its lights.

## 1.3.13 — 2026-10-05

- Give every setup step its own page on the controller's built-in screen, instead of only the rotation
  guide. The top panel carries the phone page's heading, explanation and progress; the circle shows the
  step on the controller itself: the language, the connection state (looking, permission, connected,
  partial or failed), the eight buttons' count and the 34 touch areas exactly under the sensors (held areas
  bright, checked ones tinted), the lighting preview, "this side up", and done.
- Light the controller during setup. The ring buttons follow the step: a slow wave while welcoming, a light
  going round while looking for the controller and a white fill once it connects, held buttons white and
  checked ones blue during the input check, the phone's colour preview during the lighting step, and a
  white fill when done. The ceiling light shows their average, and the screen's rim shows the same colours.
  Setup's lighting replaces the welcome screen's and the game's while setup is open; with lighting turned
  off in setup the controller stays dark. The two-second colour test is no longer needed and is removed.

## 1.3.12 — 2026-10-05

- Open setup at once when it is chosen in Settings after setup was finished. The installer gate and its
  wait for settled main-screen frames now guard only first run; setup opens right after the panel closes.
- Show setup on the controller's built-in screen. A screen with nothing on it shows setup's own page
  (the step, "continue on your phone", and on the screen step an arrow marked "this side up"), laid out
  and turned exactly like the game. Choosing a rotation turns it at once, so the right choice is the one
  that reads upright. A screen already showing the game or its welcome screen turns with the choice
  instead, unsaved until setup is finished; leaving setup restores the saved rotation.
- Add detailed settings to setup. The connection, input, lighting and screen steps open the Settings
  page itself on the matching tab (all four tabs available). Changes save at once, and setup takes over
  any of its own choices changed there, so finishing setup cannot undo them.
- Lighting test: also light the ceiling RGB when its IO4 port is open, offer the test when either the
  ring LED board or the ceiling light can be reached, and say when the LEDs are not connected yet
  instead of leaving the button out. A test colour can no longer be cleared by the worker reading it
  between its colour and its end time.

## 1.3.11 — 2026-10-05

- Tell an attached built-in screen from one showing the game. Setup's screen step says "showing"
  only when output is really running and stays on; before setup is finished it says output starts
  then, and with output turned off it says so. Output is still held back until setup is finished.
- Read "remove animations" again while setup stays open: when setup's window regains focus and
  whenever the animator scale setting changes. Turning animations off mid-setup now stops the
  backdrop, the greeting and the controller motion, and finishes any entrance or turn in place.

## 1.3.10 — 2026-10-05

- Keep the welcome and done actions on screen in short, landscape and large-text windows. The
  controller drawing shrinks to at most half the height and the text above the action scrolls;
  the greeting stays on one line, shrinking only as far as the width needs.
- Report a partial controller connection as partial. Setup's connection state now comes from the
  channels that really opened, not the chosen ports: touch failing while IO4 opens shows
  "buttons only" with a retry, and the input check and done screen say what is missing.
- Fix the built-in screen check never asking the system: its once-a-second poll overflowed on the
  first call, so an attached screen always read as not detected.
- The welcome and done backdrops and the cycling greeting follow the setup motion rules: they stop
  without window focus and stay still when system animations are off. Setup's controller polling
  also pauses without focus.

## 1.3.9 — 2026-10-05

- Redesign first-run setup as a guided flow: welcome, language, controller connection, input
  check, lighting, built-in screen and done, replacing the three icon pages. Each step shows a
  state-driven 2.5D drawing of the real controller (eight ring buttons, 34 touch areas, USB-C
  port, Aime reader and the built-in screen).
- Let setup look for the controller from the connection step on. It asks for USB permission
  there and shows waiting, permission, connected, and refused/failed states taken from the
  session, with a retry. The input check reflects real button and touch reports. Nothing is
  shown as connected or checked because an animation finished.
- The lighting step can send a two-second preview colour to the ring LEDs. The built-in screen
  step shows whether the controller's screen is attached and turns only the picture; its text
  now calls it the controller's built-in screen rather than an external monitor.
- Animation stops while setup's window has no focus and shows still frames when system
  animations are off.
- Opening setup again from Settings skips the welcome screen; Back from its first step closes
  it. Before the connection step, USB discovery still waits as in 1.3.6.

## 1.3.8 — 2026-10-04

- Size the floating Oniimai settings shortcut to its translated label instead of a fixed
  width. English no longer loses the word "settings". Keep text centered and allow the
  button to grow vertically when large text or a narrow window requires wrapping.
- Keep the compact Miuix appearance, a minimum 48dp touch target, and saved drag position.
  The installation notice, initial setup gate and controller behavior are unchanged.

## 1.3.7 — 2026-10-04

- Replace the installation-wait system toast with a non-modal Miuix notice inside the app.
  The complete Korean, English and Simplified Chinese messages wrap at the available width;
  no line-count or ellipsis limit is applied. Large text / short landscape windows can scroll
  the message, with an OK action kept visible. System bars, cutouts and the keyboard are inset.
- Repeated taps replace the notice instead of queuing duplicates. It closes on OK, after
  eight seconds (respecting the accessibility timeout), when setup opens, or on pause/destroy.
- Keep the 1.3.6 installation/main-screen readiness checks unchanged.

## 1.3.6 — 2026-10-04

- Defer first-run setup until the KanadeDX main startup screen is visibly ready. The
  streaming-file downloader, converter and cache UI no longer trigger setup after a timer.
- Check the actual main Canvas and processing UI on the Unity thread, in addition to the
  original Start button. Both the 1.60 and 1.65 profiles verify the getter before calling it.
- Show installation guidance once while waiting, and again when the settings shortcut is
  tapped. English, Korean and Simplified Chinese are included. The first setup step remains
  language selection. USB discovery/permission prompts also wait until setup is dismissed.
- Require fresh, advancing main-screen frames after a pause, focus loss or configuration
  change. If the user taps the game's Start first, setup waits until game loading finishes.
  A missing readiness signal never times out into an installer-blocking setup window.
- Keep existing users' saved settings and automatic connection behavior. Setup cancelled
  on the main screen stays cancelled for that session and is offered again on next launch.

## 1.3.5 — 2026-10-04

Redesigned external welcome screen (the pre-game lobby on the cabinet monitor), ring-button
lighting, and animated hand-off to the game on both screens. The cabinet layout and the
start gate are unchanged. Not yet validated on a physical device; see the
[preview renders](docs/UI-PREVIEWS.md#external-welcome-screen).

- **Look.** Deep charcoal openings and warm white type replace the light-gray panel and
  white circle.
- **Top panel**, laid out like a console home screen:
  - a status bar with the mark, name and time (12/24-hour as the phone is set);
  - one highlighted game tile. Its icon is a miniature of the light ring, its text carries
    the input and LED status, and its outer highlight breathes blue when Start is available;
  - a footer with the phone note and the version.
- **Main circle:** one thin ring of light just inside the edge, spilling softly onto the
  circle. It carries the button LEDs (below). Faint ripples spread from the centre: clearer
  when Start is available, and white when Start is accepted and at the hand-off. Inside are
  a state title ("Ready when you are" / "Getting the game ready"), one line of guidance, the
  Start button (warm white when available, an outline while not) and a note on what follows.
- **Button LEDs.** While the welcome screen is shown, `LobbyLights` drives the eight ring
  buttons with the same pattern as the on-screen ring:
  - one light circles clockwise while the game prepares;
  - every button glows, with a slow clockwise wave, when Start is available;
  - a short white flash when Start is accepted, from the screen or the controller.

  Brightness, rotation and reverse settings apply. Only the button colours are replaced.
  Cabinet FET channels and the settings colour test behave as before.
- **Hand-off as KanadeDX's loading screen ends.** The game phase that started the switch
  begins part-way through KanadeDX's own loading screen, so the lobby handed over mid-loading.
  KanadeDX enables its game control UI as that loading screen fades out; the first update of
  that control after Start (an existing verified UI hook) now marks the end of loading. The
  lobby stays up until then, so the STARTUP self-check, notice and title appear on the monitor.
  If the end is not reported within 180 s of the game phase it hands over anyway; sessions
  without the UI or startup-button hooks keep the earlier behaviour. No new game functions.
- **Game layout after the switch.** KanadeDX lays out its main camera and top screen from the
  screen size when it loads its main scene, and redoes that only on an orientation change.
  Output that moved to the 9:16 monitor after that kept the phone's layout: a smaller circle
  with a black ring and a top screen pushed down. For 2 s after the output moves to or from
  the monitor, the existing control-update hook now invalidates the game's cached orientation
  every few frames (`KanadeDXGameControl.prevRot`, 0xFC in both builds), so the game re-runs
  its own layout for the current surface. No game function is called by the module.
- **External output on at every launch.** Turning output off (Display tab, or returning to
  the phone from the dashboard) used to be saved, so the next launch stayed on the phone.
  It now lasts for that launch only: each new launch with a monitor attached starts output
  again, including the portrait lock at launch. The toggle summary says so.
- **Start gate fixes found while waiting for the end of loading.**
  - The 4 s orientation budget was timed from the first steady frame. After a long wait at
    READY, one configuration change (KanadeDX loading its main scene) therefore timed out at
    once, closed the welcome screen, and the retry opened the game output mid-loading. The
    budget now runs from when readiness is missing.
  - A retry or late connection while KanadeDX's loading screen is still up now opens the
    welcome screen and hands over as loading ends, instead of opening the game output.
    Only that case and the startup screen open the welcome screen (`OutputGate.opensLobby`).
    The compatibility path without hooks (20 s, no phase reported) still opens the game output,
    as in 1.3.4; an earlier 1.3.5 build left it on a welcome screen that could neither start nor
    hand over (1.3.5 review P2).
  - The timeline records `game loading screen finished` and the welcome screen's gate states.
- **Hand-off.** When the game is ready for the monitor, white fills the ring and the
  buttons from button 1, holds briefly and fades out (1.1 s) before the game's own lighting
  returns. Meanwhile the welcome screen fades to black (0.56 s). The output switches only
  if the start gate still reports the game ready after that; otherwise the hand-off is
  cancelled and the lobby returns. The timeline records `lobby hand-off` and
  `lobby hand-off cancelled`.
- **Game fade-in.** The game layer (or TextureView) fades in from black over 0.45 s once
  the bind is confirmed, or 0.9 s after binding at the latest (`output fade-in` in the
  timeline). It is never fully transparent, so Unity's frames keep being consumed.
- **Phone.** The dashboard rises in when the game moves to the monitor and fades out when
  it returns to the phone; in the background or at teardown it goes at once.
- **Motion.** Entrance, state cross-fades and press feedback on the welcome screen. Redraws
  follow the display's vsync while the screen is attached (one request per drawn frame). The
  light ring and its icon miniature each use one gradient per frame for all their strokes, and
  the light spill is rebuilt only when the ring's average colour changes; previously eight
  shaders were created per frame and redraws ran on 30 fps handler delays, which looked uneven.
  With system animations off, transitions are instant and the lobby is static (the cabinet LEDs
  still animate).
- **Copy** in all three languages is softer and shorter; `display.lobby.waiting` was added
  and the English slogan "Your controller. Your game." is gone.
- `LobbyLightsTest` covers the patterns, ring positions, flash rules, the hand-off and the
  LED worker hand-back.
- Version code 46.

## 1.3.3 — 2026-10-04

- Before game launch, the external screen shows a native module welcome screen while the
  original KanadeDX start screen stays on the phone. The layout follows the cabinet: a
  1080x450 top panel and a lower 1080-diameter circle in a rotated 1080x1920 buffer.
- The external Start button queues the game's original start callback on Unity's main thread.
  Controller buttons/touch still start the game, and output switches to the game after
  its fresh progress and orientation checks pass. No game Surface is bound for the welcome screen.
- External output can connect on the KanadeDX character/start-button screen before game launch.
  Readiness now observes the verified EventSystem Update hook and an active, interactable start
  button with a callback, without pressing it or requiring controller input.
- Keep startup and game readiness separate. Pressing Start invalidates startup readiness;
  connecting during game loading waits for fresh game progress, portrait and configuration settle.
- Use a monotonic startup/game update counter for surface lifetime checks. This remains an
  Update-based inference, not a GPU completion fence.
- Host regressions cover startup, loading, skipped phase polling and rotation. Physical-device
  startup/rotation verification is still required. Version code 44.

## 1.3.2 — 2026-10-04

Fix from the 1.3.1 code review. Not yet validated on a physical device.

- Start gate: the 20 s compatibility fallback now applies only while "hooks unavailable"
  is the current frame-source state. Before, a native status that recovered from
  unavailable (-1) to installing (-2) kept the old timer and could start output with no
  game counter. A later unavailable span reused the earlier timer too. Each unavailable
  span is now timed from its own first report, and installing waits without limit.
- `OutputGateTest` covers both transitions.
- Version code 43.

## 1.3.1 — 2026-10-04

Fixes from the 1.3.0 code review. Not yet validated on a physical device; see the
[device test matrix](docs/DISPLAY-STARTUP-TESTS.md).

- **Start gate.** A frame counter that exists but does not advance no longer turns
  ready after 20 s. The session now reports three frame-source states to the gate:
  - counter: must advance, whatever time has passed;
  - hooks still installing: waits without a time limit;
  - hooks known to be unavailable: the only state that uses the explicit 20 s
    compatibility fallback, counted from that report.
- After a stall longer than 1.5 s, readiness needs a new continuous 1.5 s run.
  One frame after a long pause is no longer enough.
- **Teardown order.** Surfaces kept because Unity's detach was not confirmed are now
  released after the game's own `onDestroy`, which shuts Unity down. Before, the
  module released them before Unity stopped.
- **Output OFF.** Saving external output OFF in first-run setup now cancels an output
  that is still waiting for the game, not only an active one. The start loop and the
  final window-open step recheck the setting, the lifecycle and the display.
- Gate tests cover:
  - a stuck counter;
  - installing versus unavailable hooks;
  - a stall followed by recovery.

  The device test matrix now spells out what "confirmed" means: an inference from the
  game's update counter, not a display event.
- Version code 42.

## 1.3.0 — 2026-10-04

Keyed i18n and English UI. Includes the 1.2.2 and 1.2.3 fixes below.
See [Translations](docs/I18N.md).

- **English** joins Korean and Simplified Chinese. Without a saved choice, the first
  supported language in the device's language list is used, else English. A previously
  saved choice is kept.
- Every UI message is a stable key with one text per language in
  `locales/en.json`, `ko.json` and `zh-Hans.json`. `scripts/generate_locales.py` compiles
  them into `Msg` constants and `I18nCatalog`, and `I18n.t(Msg.KEY, args…)` looks them up
  with English fallback.
- These replace the inline `tr(korean, chinese)` pairs and the Korean-keyed `UiText`
  catalogue.
- Messages that were built from translated fragments are now single messages with
  positional `{0}` placeholders, so each language controls its own word order. This covers
  retry countdowns, LED summaries, USB errors and display status.
- `check_locales.py` now checks:
  - identical keys, placeholders and line counts across languages;
  - no unused keys;
  - no hard-coded Korean/Chinese literals in module sources;
  - no leftover legacy helpers.

  `I18nTest` covers language matching, fallback and formatting.
- The implementation-recipe patches were regenerated for the new API and current source.
- Version code 41.

## 1.2.3 — 2026-10-04

External output start order, from the 2026-10-04 display start-up review. Not yet
validated on a physical device; see [device test matrix](docs/DISPLAY-STARTUP-TESTS.md).

- Launch policy: with external output on and a monitor attached when the game starts,
  the Activity is locked portrait before Unity's first surface exists.
- Start gate replaces the fixed 350 ms/1.2 s delays. The external window opens only after
  the game's frame hook has advanced steadily for 1.5 s, the Activity is portrait, its
  configuration has been unchanged for 0.6 s and the game has rendered since. A rotation
  that does not land in 4 s cancels the attempt into the existing retry budget.
- `displayChanged(0, surface)` is treated as "queued"; two later game frames confirm the
  switch, otherwise the status says the game did not respond.
- A detach that used Unity's whole `SurfaceDetach` timeout is not trusted: the old Surface,
  SurfaceControl or SurfaceTexture stays alive until the game renders again.
- The portrait lock lasts for the whole attempt, including retry waits, and is released only
  when output is turned off, the monitor is removed or retries are exhausted.
- Display diagnostics now include a bounded, time-stamped start/stop timeline
  (also logged as `OniimaiDisplay`) and whether the installed Activity handles rotation.
- Version code 40.

## 1.2.2 — 2026-10-04

Fixes from the 2026-10-04 code review. Not yet validated on a physical device.

- USB CDC reader: a full-length timeout stays idle, but reads that fail at once now back
  off (20 ms) and, after one unbroken second, report a transport failure so the existing
  reconnect path runs. Previously a fast `-1` loop never reached recovery.
- Input watchdog: an input device missing from the USB list is released like a detach,
  even when the detach broadcast never arrives.
- Port search runs while inputs are connected. Open input handles are kept; a missing
  touch/IO4 role triggers one clean reconnect, and a saved-but-absent input is searched
  for every 10 s. LED/NFC ports and permissions refresh on every search.
- External output: a failed bind, lost surface or dismissed Presentation retries up to
  5 times (1–8 s), and again when the game's hooks become ready or the display changes.
  User OFF, display removal and background never retry. Locking portrait from landscape
  waits for the rotation (max 1.2 s) before showing the external window.
- Port labels separate "not used", "waiting for the saved device", "duplicate devices"
  and "permission pending". Port choosers add an explicit "Automatic" option.
- Status colours use real link state: LED and Aime are green only after the device
  answers; a partial input connection is amber. The dashboard device widget receives
  the same tones.
- Version code 39.

## 1.2.1 — 2026-10-01

- Dedicated game-launch action in the home summary card.
- Tinted live-status panels for controller, card readers, external output and lighting.
- Compact dashboard actions, improved song text space, narrow device rows and a larger wide clock.
- Shared screen content extracted into `OniScreens.kt`, retaining Android callbacks and preferences.
- Light/dark UI preview gallery included in documentation and release notes.
- Version code 38 with the existing signing identity and runtime dependencies.

See [release notes](docs/RELEASE-1.2.1.md) for images, downloads and validation limits.

## 1.2.0 — 2026-09-29

Refined Miuix settings and dashboard, retaining the 1.60/1.65 native profiles.

- Module home summary, collapsing headers, selection indicators and clearer three-step setup.
- Status/control/footnote grouping, numeric validation and LED brightness presets.
- Dashboard achievement and timing indicators, larger artwork and clearer dark-mode labels.
- Widget selection/drag feedback and confirmation before discarding changed layouts.
- Version code 37; existing module signing identity and dependency versions retained.

See [release notes](docs/RELEASE-1.2.0.md) for upgrade instructions, validation and known limits.

## 1.0.0 — 2026-09-20

First standalone LSPosed API 102 module release. Tested only with KanadeDX-260207.0635 (1.60).

- Oniimai USB touch, IO4 ring buttons and P1, with named port selection and saved settings.
- Game-driven button, reader and upper-speaker RGB lighting.
- Controller card reading and phone NFC inside the game, with stale-result rejection and game error handoff.
- Rotated external display output and a configurable phone dashboard with result retention.
- Compose/Miuix settings, Korean and Simplified Chinese UI, first-run setup and offline license viewer.
- Game-build and function-fingerprint checks before native hook installation.
- English user/build/developer guides, diagrams, AI disclosure, source provenance and dependency sources.
- Light/dark README banners, GPL/MPL and dependency notices, and a license-compatible disclaimer.
