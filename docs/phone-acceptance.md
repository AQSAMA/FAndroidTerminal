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
  landscape and with the IME open. Sidebar session navigation and Tools remain accessible.
- Open two sessions, rename/switch them, rotate and return from Files/Settings while a
  process runs. Verify no session is disposed. Test shortcut help and multiline paste confirmation.
- Change font/colors/extra keys without restarting a process. Save a command and invoke
  it explicitly from Tools. Close guest sessions, change DNS/shell/directory/variables,
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
- Check Tools search/results with IME open in landscape and at large system text size;
  confirm Arabic navigation/actions and mirrored swipe gestures, keeping terminal cells LTR.

The HTML interaction study uses sample data and is not an Android screenshot. Archive/Undo, archive-lock and command-input instrumentation cases are added but have not been executed.

- Retain an edited draft, open a session via Tools, and return: Save must be disabled
  with owned-session recovery, while the draft remains editable. Close sessions and retry.
- Check localized rename/file/paste/command confirmations; verify the exact destination
  environment before Run/Paste. Creating/selecting a far-away tab should reveal it.
- Check explicit Close controls on session, file and Tools sheets without taking an action.


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
