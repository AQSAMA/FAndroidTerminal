# Terminal gestures, input and controls: physical-phone verification

Tested on 2026-11-16 with the connected Xiaomi M2012K11AG running Android 16
and Gboard. GPT-6.1 Terra medium drove the physical phone through the shared
Device tools. The user's existing Preview app, Shell 1 and Alpine environment
were preserved. Updated code was installed in the separate Debug app.

## Implemented behavior

- Opening gestures start in the measured terminal text viewport's leading half
  (left half in English). Headers, quick controls, keyboard and management pages
  cannot start opening. Vertical slop rejects navigation; horizontal intent must
  exceed vertical movement by 2x. Menu, modal dismissal and closing remain available.
- Drawer motion preserves the terminal's native text scale. Settings use a short
  entrance translation without fading through a blank frame; expanded keys use
  native expand/shrink motion. Material tonal icons and wrapping descriptions
  improve the settings list.
- IME request state is separate from observed visibility and survives session
  changes. The button reflects actual visibility. Explicit reopen requests target
  the focused Android terminal editor rather than Compose's input editor.
- The pinned terminal engine suspends PTY resizing through IME animation and
  temporary window focus loss, then uses the final viewport after a frame.
  Session tabs remain visible while the keyboard opens.
- The key editor shows all keys in wrapping rows, with an explicit destination
  selector and previous/next, move-to-other-row, remove and add actions.
  The optional Termux layout is saved through the existing settings workflow;
  existing layouts are not automatically replaced. Runtime keys retain fixed sizes.

## Observed before changes

A diagonal terminal swipe from (300, 900) to (750, 600), and Settings swipe
from (300, 1400) to (750, 1150), opened the sidebar. The old editor clipped its
expanded row horizontally. An intermediate build also exposed a repeatable
Back-then-keyboard-button failure; terminal tapping recovered it. That failure
was fixed before final acceptance.

## Physical acceptance

| Flow | Result |
| --- | --- |
| Final terminal swipe from 20% to 60% screen width | Opened twice; scrim and close button dismissed it |
| Final vertical and steep diagonal terminal swipes | Did not open the drawer |
| Always-visible and expanded quick-row swipes, both directions | Did not open the drawer |
| Header, Settings and Gboard swipes | Did not open the drawer; Settings menu still opened it |
| 200 numbered lines, scroll forward/back including diagonal movement | Passed on the preceding build; the final gesture-boundary check had no scrollback buffer, so final scroll displacement was not re-established |
| Android Back followed by Show keyboard button | Passed 3/3 after the native-editor fix |
| Explicit Hide followed by Show button | Passed 3/3 |
| Terminal-tap reopen and session switch with IME visible | Passed |
| Key move later, move to other row, remove, add, Termux preset, save and return | Responded; saved order persisted after reopening |
| Text popup and command draft sheet | Opened and dismissed |
| Appearance, Terminal & tools, Backup, Environments, Packages and CLI launcher entry screens | Opened; inspection only |

Earlier baseline inspection also opened the existing Preview Alpine file browser.
Installed-environment management, package operations, backup/restore execution,
modifier effects and custom-layout migration were not verified on the final build.
Transient flicker was not reviewed frame by frame; settled IME states passed.
Arabic/RTL, large text, TalkBack, rotation, hardware keyboards, OEM background
policy and sustained performance still need acceptance. These checks do not
establish universal compatibility or a complete visual redesign of every screen.

Local evidence is retained outside Git in /tmp: fandroid-baseline-editor.png,
fandroid-fixed-editor.png, fandroid-ime-final.mp4, fandroid-ime-final.log and
fandroid-terminal-swipe-final.png. Recordings are temporary workspace artifacts.

## Automated checks and limits

- Repository checker and diff hygiene passed.
- Debug APK and Android test APK compiled; final gesture build passed.
- Android Lint passed on the final code after the native-editor and gesture-boundary fixes.
- A 14-test physical Compose instrumentation attempt failed before test bodies:
  IllegalStateException: Exception handler was not found via a ServiceLoader.
  This matches the reported Android 16 classloader issue in
  [kotlinx.coroutines #4782](https://github.com/Kotlin/kotlinx.coroutines/issues/4782).
  None of those tests is claimed as passed. No dependency or test-framework
  workaround was shipped. The regression tests remain compiled for compatible
  test environments.
- Native PTY host tests were not rerun: byte transport, JNI and native ownership
  were unchanged. Preview/R8 and final-head CI checks run in GitHub Actions.
- No local emulator, device farm, signing identity change or Production publication.

## Follow-up phone checklist

On the Preview artifact: scroll a long buffer both ways with the IME open,
open from the terminal's left half, swipe each key row without navigation,
hide with Back and reopen by button, switch sessions, then edit/save/reopen
both key rows. Repeat with Arabic, larger text and reduced animations.
