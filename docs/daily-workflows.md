# Daily Linux workflows

The approved neutral Terax palette, connected June settings, drawer motion,
terminal input ownership and pinch sizing remain the visual foundation.

## Current implementation

- Startup chooses Android shell, environment management, session navigation or a
  specific installed instance. The connected Activity applies this once only
  after environment loading. Rebinding borrows existing service sessions.
  An unavailable target opens Environments with a Settings recovery message.
- Packages is a dedicated screen. Each environment retains its own metadata,
  architecture, installed/candidate versions and read/refresh times. Cached data
  is explicitly potentially stale. Results render in pages of 100. Selection
  is limited to 100 names; copy/share carries versions and context. Import accepts
  plain names or the copied tab-separated format, then reviews on the new target.
- apt/apk administrative commands use a clean PRoot launch and structured bounded
  process results, never terminal text. Simulation precedes installation/update
  confirmation. Locks are not removed. Closing guest sessions before operations
  is conservative admission shared with Files. Other guests remain independent.
  Repair uses dpkg configure or apk fix. Cancellation kills the owning PRoot;
  completed package changes remain and can need repair.
- Global and distribution package profiles persist in the existing workspace
  preferences. A distribution override replaces the global plan (empty disables
  extra packages). The resolved names appear before rootfs installation and are
  recorded on the instance. Provisioning follows verification; failure/cancellation
  retains Ready rootfs and durable retry/skip status. Packages offers retry, durable plan editing, or skip, with retained package output. Editable essentials and
  development presets are convenience additions.
- Preferred shells/directories apply to future environments after guest
  executable/directory checks. New environments inherit current defaults for new
  sessions. Existing per-instance settings can enable inheritance or validate explicit overrides.
  Guest settings can discover executable shells and link to package installation.
  No running session is reconfigured.
- CLI launchers persist ordered argv lists and instance/directory references.
  Each activation creates a new owned session. Paths are checked inside the guest.
  Arguments are literal, not shell interpolation. Shortcut removal never uninstalls
  a tool. An unavailable tool gives a Packages/edit repair message. The explicit OpenCode installer reuses the existing packaged, version/SHA-pinned
  ARM64 glibc installer after apt prerequisite review, then saves the launcher.
  It rejects Alpine/musl. Other tools use the guest package flow or their explicit
  official installation path; no unreviewed installation script is downloaded.
- Workspace backups select settings groups and complete environments, including
  home, hidden CLI/login files, permissions and links. Environment V3 retains
  provisioning metadata and reads V1/V2. Workspace V1 adds SHA-256 integrity and
  optional password encryption. PBKDF2-HMAC-SHA256 (210,000 iterations) derives an
  AES-256-GCM key; independently authenticated 64 KiB records bound memory.
  Sequence/size AAD and an authenticated final record reject reorder/truncation.
  Restore authenticates and validates settings/archives before promoting copies.
  Existing environments are never replaced. New identities remap launchers/startup;
  unavailable launcher references remain editable, while unavailable startup targets
  fall back explicitly. Settings-only restore retains same-device references.
  Selected settings present in the file replace their current values; other
  settings remain. Environment promotion rollback removes newly created copies
  on archive failure. A settings commit failure preserves copies for recovery.
- System/Light/Dark theme and terminal-only, resumed-Activity screen-awake mode
  persist. Quick keys have accessible reorder buttons and existing isolated drafts
  with explicit Save feedback.

## Evidence and limits

Focused host tests exercise package parsing, input rejection, cancellation,
backup round trips, encryption across records, wrong passwords, integrity failure,
validation-before-promotion and staging cleanup. Android UI tests compile but need
phone/device execution. No emulator is used.

Metadata snapshots are service memory, not a promise of process-death restoration.
Package selections and updates can be reviewed across environments and then
executed sequentially with separate outcomes. There is no atomic cross-environment
package transaction; completed changes survive cancellation or later failures. Simulation is advisory because
repositories can change before execution. The shell/tool installation path uses
the package browser, the explicit pinned OpenCode ARM64/glibc installer, or an
explicitly user-run official installer; it is not a universal installer for
arbitrary architectures/libcs.

Backup restore authenticates a preview and lets users select individual bundled
environments or settings only; it does not offer per-file restores or replacement
of existing trees. Full backups need
substantial temporary internal storage (authenticated payload plus staged archives).
Exports interrupted by cancellation must be discarded. Passwords remain memory-only
and cannot be recovered. Android Keystore material and expired/revoked/server-bound
credentials may require login again. Processes are never backup contents.

The existing host renders predate the newly added functional screens. Neither those
renders nor JVM tests establish phone layout, IME, gestures, native guest execution,
OEM background policy or performance for this revision.


## Independent review and follow-up

Fresh critic A (gpt-6.1-sol/high, Standards) and critic B
(gpt-6.1-terra/high, Spec) independently reviewed commit 4862116 against 7925bd8
using the same source, continuation brief and check log. Both completed; no
phone or new-screen visual evidence was claimed.

Material findings were addressed in the follow-up: strict JSON preference types;
retaining/remapping settings-only launcher/startup references; closed command
stdin; service-lifetime startup generation and service-owned initial launch;
snapshotting defaults before asynchronous save; durable profile edit/retry and
diagnostics; shell defaults after failed/skipped provisioning; individual restore
selection; labeled selection rows; localized operation/recovery states; contextual
launcher repair and guest tool checks; cross-environment package review/outcomes.

New focused tests cover stdin EOF and selecting one authenticated environment.
Portable-settings type/reference regression tests are Android instrumentation
tests: compiled for delivery, with execution deferred to an explicitly authorized
device check. Follow-up fixes were inspected by the primary agent; the critics
did not approve the later revision or supply phone evidence.
