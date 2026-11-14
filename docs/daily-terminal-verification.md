# Daily terminal continuation

Historical verification for PR 8 b35e3761. The subsequent phone-feedback redesign
and added Ubuntu/Alpine support supersede its scope; see [redesign review](redesign-review.md).

Base: origin/main 7287c660 (merged PR 7). Actual supplied phone captures identify
Preview source a31a5b1e5d23. They are earlier real app images, not captures of these
changes. No mockups or new app recordings were produced. No emulator was launched.

## Chosen gaps and implementation

- Persistent font resize: the pinned engine only magnified a layer until release.
  Two fingers now adjust real font/grid size at 8–28 sp. Release saves the global
  device preference. Text → Terminal text size provides +/- without gestures.
- Session navigator: closed Terminal had disabled drawer gestures. An opening strip
  at the locale's start edge owns horizontal navigation; the terminal owns other
  gestures. Content translation, slight scaling and corners follow drag progress.
  Release settles by position; cancellation returns to the starting destination.
  Back and Escape dismiss the modal first; the wide permanent navigator remains.
- Input: quick keys can include /, -, | and ~ with modifier-aware character dispatch;
  Text → Customize keys reaches existing persisted settings. At 16 keys additional
  choices are visibly disabled and explained. Extra keys expand with native motion.
- Environments: View sessions chooses an existing process; New session is a separate
  explicit action. Existing safe file/settings recovery includes archived sessions.

## Independent critique

Two fresh independent critics per round: Codex gpt-6.1-sol/high and
Codex gpt-6.1-terra/high. Initial ratings: A visual 6.5, usability 6, reliability 6.5
(provisional); B visual 7, usability 6, reliability 6 (provisional). These describe
older captures and source, not validated phone behavior. Both found transient pinch,
disabled closed-drawer swipe, and keyboard switching friction.

The second fresh pair reviewed changed source and test/build evidence. Findings:
modal input focus leakage, interrupted animation jumping, Back-handler ordering,
silent key-limit rejection, and incorrect test API property. These were corrected.
An attempted emulator proxy was rejected by compilation and concrete engine-type
requirements; it was removed entirely in favor of a native focus sink. No such
proxy is part of the delivered implementation. A third fresh pair checked the final implementation and identified stale gesture
font state and a close-at-zero gesture owner removal; both were corrected, with
a regression case holding the pointer through recomposition. The opening strip
leaves top/bottom toolbar areas available. No remaining release-blocking source
regression was proven. IME focus during a session switch while the closing spring
is running remains a phone-verification risk. No revised visual or motion scores
are warranted without new phone captures. Acceptance remains pending.

## Verification

Repository check and Python tooling tests passed. Native Linux PTY host test passed;
it does not verify Android policy, IME, or phone performance. The first Android pass
passed runtime JVM tests, lint, Debug and Preview builds. Later instrumentation
compilation exposed a test property typo, corrected from cols to columns. Another
intermediate pass rejected the removed proxy. Final build results and CI links are
reported in the PR/handoff; instrumentation tests are compiled only, not executed.

## Phone acceptance checklist

1. Install the PR Preview. Record its build/source from About; capture video rather
   than stills for the following motion checks, in English and Arabic if possible.
2. Start Android shell; verify app permissions, then launch an installed Debian
   guest. From Environments, View sessions must reuse a process; New session creates
   another. Archive/restore and switch; confirm output/history and running work stay.
3. With keyboard open, slowly drag from the start edge, reverse, release halfway,
   cancel, interrupt settling, close from the exposed content, and press Back/Escape.
   Verify terminal scrolling/selection away from the edge, keyboard continuity,
   navigator search focus and no hidden command input while the sidebar is open.
4. Pinch larger/smaller and at both bounds. Release one finger before the other.
   Verify text stays changed, `stty size` follows the grid, no input is sent, and
   session/history survive switching, rotation and background/foreground transitions.
   Open Text → Terminal text size; compare +/- and Appearance; restart the app to
   verify the preference (running processes do not survive process death).
5. Text → Customize keys: save symbols/navigation keys; reopen and test Ctrl/Alt
   one-shot behavior, Escape, Tab, arrows and symbols with keyboard open. Verify
   selection/copy and multiline paste confirmation; selection order remains key order.
6. Check View sessions at session limit, archived sessions, safe close confirmation,
   Files recovery, install cancellation/retry and existing environment files.

## Remaining limits

No current-build phone images, gesture recording, TalkBack, hardware keyboard,
OEM lifecycle, download/install recovery or physical performance results are
available. Visual/motion acceptance is pending; continuous reference playback was
not visually observable. No extra distributions, root backend, simultaneous live
GUI guest-file access or process-death session recovery was added. Direct drag
reordering of quick keys remains absent; selection order controls their order.
