# Settings redesign verification

Scope: compact terminal quick controls, the key layout editor, Working mode and
Terminal & tools. Material3 roles, the existing font family and native ripple,
48dp touch targets, selected-row color transitions, size animation and disclosure
animation form the design system. App UI follows locale; terminal/key order and
command strings remain LTR.

The original phone capture shows a full utility row below the primary keys.
The dock now measures 48dp collapsed and 96dp expanded. A focused instrumented
regression asserts both dimensions; it has not been executed on a phone here.
Existing input-menu interaction tests now expand the second row before opening
that menu.

Host evidence uses the actual production composables and the official Compose
Preview screenshot plugin alpha16 through Gradle. Temporary configuration and
fixtures are under ignored `build/ui-previews/render_host.py`; they are restored
in `finally`. No APK or emulator is launched. Permission/preferences are fixtures;
root, Shizuku and storage operations are not executed. English light/dark, Arabic,
small phone/large font, Arabic large-font Working mode/Tools, and collapsed/expanded
key controls are rendered (13 previews total). These are layout
proof, not Android keyboard, policy or animation-performance proof.

Phone acceptance:

1. Open/close the IME twice. Check the quick keys meet the IME with no empty row;
   expand, open the contextual menu and send a key without losing terminal focus.
2. Select each key row, add, reorder, move and remove keys. Save, reopen and rotate;
   check the order and row assignment persist. Try Arabic and a large system font.
3. Open Working mode, authorize a provider and expand the explanations/recovery.
   Grant storage access, create the shared folder and toggle one environment;
   verify new sessions receive it and existing sessions keep running.
4. Change startup/awake settings and saved commands. Save and reopen; copy the full
   directory path and open a Linux folder. Check light and dark themes.

## Review ledger

Requested reviewer: `gpt-6.1-sol`, reasoning effort `high`, Codex provider.
Exactly two visual/code review rounds completed. A first launch failed
at the account usage limit without a verdict and was replaced after resuming.

- Round 1: APPROVE; no actionable blocker, should or nit findings. Independently
  checked the compact dock, grouping, editor operations and supplied light/dark,
  Arabic and large-font renders. Noted that phone IME, TalkBack and motion remain
  unverified. Additional Arabic/large-font Working mode/Tools and expanded-dock
  renders were prepared for round 2. No behavior changes followed round 1.
- Reviewer token usage was not exposed.
- Round 2: APPROVE; no actionable blocker, should or nit findings. Reviewed the
  final diff and added Arabic/large-font Working mode/Tools and expanded-dock
  coverage. No findings required fixes or dismissal in either completed round.

## Checks observed

- `python3 scripts/check_repository.py`: passed.
- `git diff --check`: passed.
- Gradle `:app:updateDebugScreenshotTest`: passed, 13 Layoutlib renders, with
  temporary alpha16 configuration restored after rendering.
- Gradle `:app:compileDebugAndroidTestKotlin :app:lintDebug`: passed.
- Instrumented execution/physical phone acceptance: not run. No local APK build,
  emulator, device farm or Android runtime acceptance was used for these checks.
