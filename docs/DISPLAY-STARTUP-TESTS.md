# External output start: device test matrix

Use this to separate the two reported crashes: monitor attached at launch, and a
landscape game with a monitor connected later. It describes the start order as of
1.3.3. Host tests cover the start policy only. They do not exercise Unity, Android
windows or GPU drivers.

## Start order

1. **Launch policy.** With external output on and a monitor attached when the
   game Activity is created, the Activity is locked portrait before Unity's
   first surface exists.
2. **Start gate** (`OutputGate`). The external window opens only when all of
   these hold:
   - the current startup UI or game progress counter has advanced without a gap longer than
     1.5 s for the last 1.5 s. A longer stall starts the measurement again;
   - the Activity is portrait;
   - its configuration has not changed for 0.6 s;
   - the active phase has updated again since that last change;
   - external output is still enabled, the app is in the foreground and the
     display is still valid. These are rechecked just before the window opens.

   Before pressing the character/start button, the game's input Update counter is zero.
   The verified EventSystem Update hook supplies startup progress only while the button is
   active and interactable and its start callback exists. This does not press the button.
   Pressing Start invalidates startup readiness; game loading waits for fresh game updates.
   A phase change restarts the 1.5 s stability measurement, even if a poll skips loading.
   A shared monotonic update count spans both phases for bind/detach checks.

   The frame source has three availability states:

   | Frame source | Gate behavior |
   |---|---|
   | Hooks installed (a counter, even 0) | Must advance; elapsed time never approves it. |
   | Hooks still installing (not probed yet, or waiting for the game library) | Waits without a time limit. |
   | Hooks known to be unavailable (native load failure, no hook API, unsupported build, install failure) | Explicit compatibility fallback: 20 s into the current unavailable span. Returning to "installing" ends that span; a later failure starts a new one. |

   If portrait is missing for 4 s while the game is ready (timed from when readiness was
   lost, so a long wait at READY does not use up the budget), the attempt stops and uses
   the normal retry budget. A game whose frames stop for good stays
   in "waiting for the game screen". Turning external output off cancels that
   wait, as it cancels retries and active output, from the Display tab or from
   first-run setup.
   In the startup phase, the Presentation contains only native module information and a
   Start button. Unity keeps rendering on the phone. A 250 ms observer switches to the
   external game surface only after the GAME phase passes the same readiness gate.

   The GAME phase begins part-way through KanadeDX's own loading screen ("KanadeOS"),
   which is followed by the STARTUP self-check, the notice and the title screen. KanadeDX
   disables its game control UI at boot and enables it as the loading screen fades out, so
   the first update of that control after Start (the existing verified UI hook) marks the
   end of loading. The lobby hands over then:

   | Loading-end signal | Lobby hand-off |
   |---|---|
   | Reported after Start | As the loading screen ends, if the gate is ready. |
   | Hooks installed, not reported yet | Waits; after 180 s of game phase it hands over anyway. |
   | UI or startup-button hooks unavailable | At the first ready game phase, as before 1.3.5. |

   The welcome screen opens only on the startup screen, or in a game phase whose loading screen
   has not ended. With no hooks at all (the 20 s compatibility path, no phase reported), a ready
   gate opens the game output directly, as in 1.3.4.
   USB controller startup input is unchanged. The external Start action queues the
   original game button callback; it never calls Unity objects on the Android UI thread.

3. **Bind check.** `displayChanged(0, surface)` returning `true` means only that the
   switch was queued on UnityMain. The timeline says `bind confirmed` after two
   further game frames.
4. **Detach check.** A `displayChanged(0, null)` call lasting Unity's whole
   `SurfaceDetach` timeout is not treated as success. The old surface (and the
   SurfaceTexture on the TextureView route) is kept until Unity renders two more
   frames; without a frame signal, for 5 s. When the game Activity is destroyed,
   kept surfaces are released only after the game's own `onDestroy` (which shuts
   Unity down) has returned.

"Confirmed" in steps 3 and 4 is an inference from the game's update counter and
from call timing. It is not a display-complete event from Android or the GPU, and
a passing host test does not verify the GPU switch.

## Conditions

Run every row on the same phone, game APK (note whether it is NPatch), module
APK and settings. Repeat each row at least five times.

| | Monitor | Phone orientation at launch |
|---|---|---|
| 1 | attached before launch | portrait |
| 2 | attached before launch | landscape |
| 3 | connected after the game reaches the title | portrait |
| 4 | connected after the game reaches the title | landscape |

Additional cases:

- connect from the character/start-button screen without pressing Start, with and without
  controller input connected; the external module welcome screen must appear while the game stays on the phone;
- after connecting at the character screen, press Start using the controller and finish loading;
- test the external Start button (touch-capable display) separately from physical USB controller
  input; it calls the same game callback and must not fire twice;
- repeat with both external rotation settings; top information must stay in the rectangular
  opening and Start within the lower circular opening;
- press Start and watch the phone: the lobby must stay up through KanadeDX's loading screen
  and hand over as it ends, so the STARTUP self-check appears on the monitor (`lobby: game
  loading, waiting for the end of its loading screen`, `game loading screen finished`, then
  `lobby hand-off: loading screen
  finished` in the timeline);
- with LED output on, check the ring buttons against the welcome screen's ring:
  - the light circles clockwise from button 1 while loading;
  - all buttons glow when Start is available;
  - one white flash on Start (also with the LED rotation/reverse settings changed);
  - when the game is ready, white fills the ring from button 1, the screen fades to black,
    and the game fades in (`lobby hand-off`, `lobby finished`, `output fade-in` in the
    timeline). The game's own lighting returns about 1.1 s after the hand-off began;
  - rotate the phone during the hand-off: it must cancel (`lobby hand-off cancelled`) and
    the lobby must return;
- press Start while a connection request is pending; do not switch using stale startup progress;
- game loading that takes longer than 20 s;
- turning external output off (Display tab, and again via first-run setup) while
  the output is still waiting;
- after the switch, the circle must fill the round opening and the top screen the rectangular
  one; return to the phone and check the phone layout is restored as well;
- turn output off, close KanadeDX and launch it again with the monitor attached: output must
  start again (`external output on again for this launch` in the timeline);
- leaving or recreating the game after a `detach … not confirmed` line;
- playing while a partial USB connection is being restored.

Record 1.60 NPatch and 1.65 LSPosed results separately.

Controls: the same rows with external output OFF, and with the original game
(no module), help tell module effects from game or driver behavior.

## What to collect

- **Oniimai settings → Display tab → Copy display diagnostics.** The *Display timeline* lists,
  in milliseconds since the session started:
  - session facts: module version, `activityHandlesRotation`, graphics start mode,
    Unity detach timeout;
  - `output updates started phase=1|2`, `game hooks ready`, each `configuration …` change and
    gate state;
  - `presentation show`, `bind queued` / `bind confirmed`, `open skipped …` and
    `start cancelled …`;
  - `detach … confirmed|not confirmed` and any retained-surface release.
- `adb logcat -b all` with tags `OniimaiDisplay`, `OniimaiKanade`, `Unity`.
  Each timeline line is also logged under `OniimaiDisplay`.
- For a native crash: the tombstone (`adb bugreport`). Note its first frames
  (for example `libvulkan.so`, `GetSwapchainImagesKHR`).
- The NPatch-embedded module version. The installed module APK can differ from
  the one patched into the game.

Use the timeline to distinguish startup (phase 1), loading (no advancing phase), and game
updates (phase 2). A crash before `presentation show` has no external Surface bind in that
attempt; orientation/startup hooks still need investigation. A crash between `presentation show`
and `bind confirmed` warrants checking the Surface switch, but the timeline alone does not
establish the cause.
