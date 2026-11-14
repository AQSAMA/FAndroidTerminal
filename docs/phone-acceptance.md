# PR 1 phone acceptance

Status: the user installed PR 1’s Preview on their phone and reported that everything they tested works. This is user-reported PR 1 phone acceptance; device details, versions, and individual test results were not supplied. The checklist below remains a reproducible reference, not a claim that each item was run. Host tests and CI are separate evidence; they do not
prove Android ptrace policy, IME, Bun/OpenCode compatibility or physical-device speed.
Use the signed Preview from this PR's Preview release, on an ARM64 phone (Android 10+).
Do not uninstall an existing Preview unless you intend to erase its private data.

## Install and manage

1. Keep the Android shell available. Tap **Linux**, name an instance `Debian work`, then
   **Install Debian**. Observe download, extraction and guest-probe stages. Ready must
   appear only after the probe passes. Record any error verbatim.
2. Open two terminals in that environment. Run `echo work > /root/instance-name` in one
   and `cat /root/instance-name` in the other. Run `sleep 120; echo survived` and switch
   tabs, rotate, background and return. Terminal state/processes must survive Activity recreation.
3. Install `Debian personal` using the same flow. `/root/instance-name` must be absent.
   Rename it, close/reopen the app and verify both names, packages and files persist.
4. Cancel another installation during download and extraction; retry. Force-stop during
   setup; reopen and retry the interrupted record. Neither existing environment changes.
   Force-stop after Ready ends sessions, but reopening an installed environment works.
5. Turn off networking during a download: an actionable failure, retry, no Ready state.
   Restore network and retry. Check low-storage failures if practical.
6. Delete stays disabled with associated session tabs (including finished tabs). Close
   those tabs, delete the personal instance and confirm the permanent-data warning.
   The work instance's files and Android shell must remain intact.
7. With the keyboard open: type, paste, Ctrl-C, Tab, arrows; open/close the environment
   picker and rename dialog. Check portrait, landscape, large text, light/dark themes.

## Real Linux programs

In the work instance:

```sh
uname -m; dpkg --print-architecture; cat /etc/debian_version
fandroid-tools base
python3 - <<'PY'
import pathlib, ssl, sqlite3, subprocess, urllib.request
pathlib.Path('/root/python-proof').write_text('persistent')
print(ssl.OPENSSL_VERSION, sqlite3.sqlite_version)
print(subprocess.check_output(['git', '--version'], text=True))
print(urllib.request.urlopen('https://www.debian.org', timeout=30).status)
PY
python3 -m venv /root/venv
/root/venv/bin/python -m pip install packaging
/root/venv/bin/python -c 'import packaging; print(packaging.__version__)'
git clone --depth=1 https://github.com/octocat/Hello-World.git /root/hello-world
git -C /root/hello-world status
fandroid-tools opencode
opencode --version
cd /root/hello-world && opencode
```

Connect a provider in OpenCode and ask it to read README and create a small file. Verify
that file from a second terminal. Test interactive input, cancellation and reopening the
CLI. `--version` alone is not acceptance of the TUI or provider execution. Credentials
stay on the phone; do not paste tokens or shell history into the issue.

`fandroid-tools base` uses Debian's signed apt repository packages. OpenCode helper pins
**1.19.5 Linux ARM64 glibc**, official release asset SHA-256
`fae5be262d693b7c5e562856cc473e59e741772936db0c0600ac0d5ed5bd088b` (78,513,091 bytes).
It tests the binary before installing it, avoiding npm/link2symlink installation races.
It is an explicit optional guest download, not bundled code or a host runtime. This
version is selected for reproducibility, not a claim that it has passed phone acceptance.
Source: https://github.com/anomalyco/opencode/releases/tag/v1.19.5.

Record phone model, Android version, APK commit, durations, Debian/package/Python/Git/
OpenCode versions, pass/fail and exact errors. If OpenCode fails, preserve the environment
and report it; do not infer success from apt or from another application's results.

## Pinned installation input

Debian 13.7 trixie slim, ARM64, debuerreotype build 20261005, recompressed by Horus.
URL: https://github.com/scarif-labs/horus/releases/tag/rootfs-debian-13.7-20261005
Archive SHA-256: `c4a40504d8eddd7d47125d129296352d3758b1d0ce958bd517b44631aee74eb5`.
Size: 17,386,584 bytes. Uncompressed upstream tar digest:
`258ba1f62b513e7bb7492d3cc8ce445e4ef176a14980f193f789b6d7270ae61c`.
No mutable latest image or arbitrary user URL is accepted.

Guest DNS defaults to 1.1.1.1. HTTPS starts with Android's accepted CA roots; installing
ca-certificates switches to Debian's maintained bundle. TMPDIR is instance-local /tmp;
PRoot scratch files are in that instance's private runtime-tmp directory. Compatibility
mode disables seccomp acceleration by default. These essential defaults persist per
instance; editing DNS/compatibility and terminal/input preferences belongs to PR 2.
PRoot shares the app UID/kernel; environments are separate file trees, not security sandboxes.

PR 2: terminal/input preferences, configurable DNS/compatibility, files and remaining
operations such as export/import/backup. PR 3: fixes from phone acceptance, reliability,
performance and visual polish. Additional distributions are deferred to keep Debian complete.

## PR 2 phone checks (pending)

- Update the existing Preview in place; confirm PR 1 environments, packages and files remain.
- Navigate Terminal / Environments / Files / Settings in light/dark, large text, RTL,
  landscape and with the IME open. Sidebar session navigation and Search commands remain accessible.
- Open two sessions, rename/switch them, rotate and return from Files/Settings while a
  process runs. Verify no session is disposed. Test shortcut help and multiline paste confirmation.
- Change font/colors/extra keys without restarting a process. Save a command and invoke
  it explicitly from Search commands. Close guest sessions, change DNS/shell/directory/variables,
  and verify only new guest sessions use the settings; Android shell stays clean.
- Close that environment’s sessions before GUI file access. In Files, navigate breadcrumbs/recent locations, create/edit, rename/copy/move/delete,
  follow a guest absolute link, import/export with SAF, and open Terminal here.
- Close an environment’s sessions, clone it, change a clone file and verify independence.
  Export a backup, restore as a new instance, and verify packages, links, executable files
  and guest settings. Cancel and retry operations; existing instances must remain usable.
- Recheck package installation, Python, Git and OpenCode workflows from PR 1.

PR 3 remains acceptance fixes, reliability, measured phone performance and polish from
actual use. No PR 3 work is included or authorized by PR 2.

### PR 2 phone-feedback UI follow-up (pending on the updated Preview)

- Check the environment/session title and quick tabs for multiple sessions, including long mixed
  Arabic/English names. New terminal must create a session; installation lives only in Environments.
- Swipe a session to archive; check that its process continues. Undo must restore the
  selected session, including after another session becomes visible. Restore from Archived sessions.
- Swipe in the other direction, check the named environment/session confirmation, then
  cancel. Check the same actions through long press and the icon menu with TalkBack.
- With visible and archived Linux sessions, Files/settings explain the lock and open
  only that environment’s session manager. Close all of them; Files should load automatically.
- Inspect links to files, folders and missing targets: Follow opens the target; copy,
  rename/move and delete affect the link. Folder targets must not offer file export.
- Check Search commands search/results with IME open in landscape and at large system text size;
  confirm Arabic navigation/actions and mirrored swipe gestures, keeping terminal cells LTR.

The HTML interaction study uses sample data and is not an Android screenshot. Archive/Undo, archive-lock and command-input instrumentation cases are added but have not been executed.

- Retain an edited draft, open a session via Search commands, and return: Save must be disabled
  with owned-session recovery, while the draft remains editable. Close sessions and retry.
- Check localized rename/file/paste/command confirmations; verify the exact destination
  environment before Run/Paste. Creating/selecting a far-away tab should reveal it.
- Check explicit Close controls on session, file and Search commands sheets without taking an action.


### Terax-led PR 2 workspace follow-up (phone checks pending)

- Inspect the first character and top row while typing at the left edge: regular/bold,
  8/14/28 sp, portrait/landscape, light/dark and open IME. The measured terminal now has
  outside gutters; exact clipping recovery has not been reproduced on an updated phone.
- Sidebar is the cross-environment session navigator. Search session/environment names,
  switch Visible/Archived, restore, swipe, cancel Close, and Undo. Within-environment quick
  tabs are separate shortcuts; the title no longer opens a duplicate global picker.
- Terminal overflow → Focus mode (or Ctrl+Shift+F) hides navigation chrome; use Exit focus
  mode or Back to return. Confirm the same process/output survives.
- Check fixed Paste/keyboard/text controls beside the scrolling key row with IME open.
  Select text by long press or the text menu; Copy/Clear sit near selection. Hide/show IME
  and tap the terminal to return to input; confirm hardware Ctrl/Alt and shortcut behavior.
- Text menu → Write command opens a separate per-session draft. Close/reopen, switch
  sessions and rotate: drafts must remain separate. Review and send → Cancel runs nothing.
  Confirm explicitly at a shell prompt: text plus Enter go to the named process. No AI,
  automatic history logging, shell block integration or external account is involved.
- Revisit Files in two environments: directory, editor draft and environment identity must
  stay distinct across navigation. The session-access limitation remains; manage owned
  visible and archived sessions, close explicitly, then return to Files.
- Check Graphite under Appearance; existing saved color choices must remain intact.

Terax and cmux snapshots/source/screenshot maps are ignored study references. No code or
images from either are shipped. No native after-change screenshot or phone acceptance is
claimed from HTML studies or static review. CI compiles/checks the app. The added connected
input/draft/navigation regressions remain uncompiled and unrun without an explicitly requested device run. Adding a separate instrumentation-APK build step was deferred because the current GitHub OAuth connection lacks workflow-write scope; the app uses the existing CI pipeline.

## Workspace usability continuation

These checks require an actual Android device; source review and successful APK
builds do not establish visual quality or IME behavior.

- Open a shell, show the keyboard, use Esc/Ctrl/Alt/Tab and scroll the key row.
  Check the selected modifier and release after input at large font sizes.
- Open Search commands (command icon or Ctrl+Shift+P). Confirm that selection actions
  also remain available beside terminal text and session actions beside sessions.
- Change Appearance without saving, return to Terminal using Back, rotate, and
  return to Settings. The same subsection and draft should remain; running sessions
  and saved preferences should be unchanged. Repeat with environment settings.
- Install/cancel/retry Debian in Environments. Check actual stage text and Cancel
  belong to that card; navigate to Terminal and check progress names the environment.
- With live and archived sessions, open the environment card: see the file restriction,
  review its sessions, cancel a Close confirmation, and restore an archived session.
  Close them deliberately, enter Files, then return to the terminal.
- Exercise Paper and dark palettes, Arabic UI, long names, many sessions, 320 dp
  portrait, landscape, and large text. Visible/Archived filters should wrap.
- Trigger an operation error, dismiss its banner, and retry from its environment.

No current Android screenshots were available during the source audit. Independent
source findings were addressed; visual scoring awaits real app captures.

Follow-up journeys from the independent critic and newcomer walkthrough:

- On a new install with the automatically opened Android shell, find the Debian
  introduction, open environment management, return and dismiss the introduction.
  Confirm dismissal survives reopening; the shell keeps running throughout.
- In Review sessions, Close then Cancel: the same scoped list should remain.
  Confirm Close for each session: the list remains until Continue returns to the
  originating page. Archive and restore must still preserve processes.
- Edit guest settings, save and wait: Saving should become Up to date, and Save
  stays disabled until another edit. Enter an invalid path: retain the draft and
  show the failure. Navigate away during save and return to check the final values.
- Browse nested directories: toolbar and system Back move to the parent; editor
  Back first handles its draft; Back at / returns to Terminal. When Files is locked,
  Return to terminal selects an existing session of that environment without
  creating another session. New terminal here must clearly mean a new process.
- Trigger Export backup with sessions present: the error should scroll into view,
  offer Dismiss and Review sessions, and clear after resolving the restriction.
- Open command search by touch and hardware shortcut: focus the field immediately,
  enter an unmatched query, clear it and select a result. Check keyboard focus returns.

Source reviews before these fixes both rated the interaction completeness 6/10;
those were heuristic source assessments, not visual or phone acceptance ratings.

Final recovery checks:

- Fail a guest-settings save, then restore the original field values: the clean
  state says Up to date, never falsely claims the failed request succeeded.
- Scroll away from an environment being installed: the fixed operation/Cancel
  strip remains available even when its card is offscreen.
- At the twelve-session limit, an exited terminal offers Close a session first,
  opening session navigation instead of repeatedly attempting a failing creation.
- Reject an editor paste exceeding 1 MiB with a visible error and retain the
  previous draft. Check About opens the same licenses dialog directly.

## Substantial workspace refactor after actual phone captures

The twelve supplied screenshots received independent 4.5/10 assessments. Earlier
source/mockup scores did not establish the app's visual quality. These captures
show the old interface, not proof of the refactored result.

- Terminal header: New session is always visible. Overflow opens a compact menu
  with Rename, Archive/Restore, Focus and separated Close session; confirmation
  still names the target. Management pages have one leading Back control.
- Ctrl and Alt remain visible while other extra keys scroll. The input menu owns
  Paste, Select visible text, Write command and Commands. Selection exposes Copy
  and a visible dismiss action. Test modifier release and keyboard toggling.
- With the IME already open, open Commands from the input menu or Ctrl+Shift+P.
  The dialog must use the area above the keyboard. Identity/search stay visible
  while saved commands scroll; destinations and session actions are absent.
- Open Write command: identity and dismissal remain visible above the IME. Review
  and cancel without execution, return to the retained draft, then confirm a safe
  command. Recheck rotation, large text, landscape and a hardware keyboard.
- Session navigator: search is on demand; archived sessions are a secondary list;
  Environments and Settings sit below the session list. Test many long names and
  keyboard-visible navigation, including archived running processes.
- Install Debian: one pinned environment card owns detailed progress and Cancel.
  There is no duplicate global Cancel or disabled Retry while installing. Terminal
  shows a compact link to the operation; Settings has no installation banner.
  Ready offers Open terminal; errors retain reason, Details and relevant retry.
- Files belongs to its environment card and discloses how many sessions block it.
  Back from the Files root returns to Environments; existing-session return never
  creates or terminates a process. Archived sessions remain part of the lock.

Review follow-up for the structural refactor:

- While installing or backing up environment A, open a terminal in ready, idle
  environment B directly from its card. Management admission remains global;
  terminal launch only respects its own environment and the session limit.
- Back up/restore a deeply nested filename at large text size. The pinned stage
  stays within two lines; Cancel stays reachable and Details shows the full path.
- In landscape with the IME open, Commands keeps identity/search fixed while
  actions and results scroll; the compact header avoids a redundant title.
- The input menu has a dropdown indicator and tooltip. Current-environment Files
  remains available from the terminal overflow and uses the same lock-aware flow.

Focused EnvironmentCard UI regressions cover unrelated-operation launch and long
progress-path details/cancellation. They are compiled locally, not executed on a
phone. The screenshot-based baseline score remains 4.5/10; no revised visual score
is claimed until the new build is captured and independently assessed.

## Reference refinement pass (revised phone captures pending)

The 23 supplied phone captures of build 1031 / de4a10b71519 received an independent
6.5/10 visual critique against the 54 reference images. This rates the supplied build,
not the revised application. Review of new phone captures remains required.

- Update Preview in place. With two sessions, open the keyboard: quick tabs disappear,
  the selected session/process stays unchanged, and the terminal resizes. Switch using
  the drawer, dismiss IME and confirm tabs return. Repeat in landscape and Arabic.
- Use Ctrl+C, Alt and navigation keys; armed modifiers remain visible and release after
  input. Use Keys to expand/collapse the extra-key row; all four arrows should be visible together
  at the default key selection. Keep it expanded across recreation, then scroll to Home. Input opens Paste, Select visible text, Write command
  and Commands; check copy, multiline-paste confirmation and keyboard show/hide.
- Open settings, edit appearance and extra keys independently, navigate Back and recreate
  the Activity. Drafts remain separate. Clean pages have no disabled save panel; changes
  expose Save; success is compact. Check 320/360 dp widths and larger system text.
- Compare Android shell with Linux entries in the drawer and Environments. Install opens
  the verified Debian release/download/storage summary and editable name. At open IME,
  the name, validation failure and Cancel/Install must be reachable. Cancel installation,
  retry, rename and reopen the resulting environment without losing files.
- With visible and archived Linux sessions, Files/settings explain the lock. Review
  sessions and use direct Close: Cancel returns to the same list; confirming closes only
  the named process. Continue after resolving the lock. No bulk close or archive-as-close.
- Capture terminal + IME, drawer, Input menu, appearance clean/dirty, install + IME,
  environment card and session recovery sheet in light/dark. Use a fresh independent
  critic for each new visual round. Host renders are component evidence, not phone proof.
