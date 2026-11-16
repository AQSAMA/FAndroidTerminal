# Sources used by the application

Only sources already compiled into, or directly adapted into, the application are
listed here. `dependencies.lock.json` records their exact upstream revisions.

| Component | Upstream | Integration |
| --- | --- | --- |
| Terminal emulation, rendering and input | [ConnectBot termlib](https://github.com/connectbot/termlib) at `7805c30f897ecf09b6b3bb5b37d92d062253ac82` | Unmodified build dependency fetched into ignored `third_party/termlib/`; host adapter in `third_party/termlib.gradle.kts` |
| PTY and JNI | [Termux](https://github.com/termux/termux-app) at `8629e632fcb95da272221be327db653fb24befe9` | Adapted files in `third_party/termux-pty/termux.c` and `core/runtime/src/main/java/com/termux/terminal/JNI.java`; changes in `patches/termux-pty.patch` |
| Connected groups and floating controls | [June](https://github.com/DenserMeerkat/June) at `f7501172211624bdfdc2690a8c6794d2483bfafb` | `JuneConnectedList.kt` and `JuneFloatingActionBar.kt` adapted into `core/designsystem/src/main/java/io/github/aqsama/fandroidterminal/designsystem/ConnectedGroup.kt` |

## Local references

Primary study snapshots live directly under ignored `references/`; secondary ones are
under `references/secondary/`. The local `references/README.md` maps sources to tasks.
They are source snapshots of the latest upstream default branch when fetched, without
Git history. They are not Git submodules, shipped application code, or build inputs. Their local inventory and activity assessment
stay inside that ignored directory. Updating them does not upgrade the compiled
terminal dependency or alter the provenance of code already adapted into the app.

Run `scripts/bootstrap.sh` on a fresh clone to fetch the compiled dependency at
its tested revision. Keep that revision pinned until an upgrade is separately
validated. See `THIRD_PARTY_NOTICES.md` for the sources actually distributed.

## Linux implementation (PR 1)

Horus 02d6fdd: SafeTarExtractor and adversarial fixtures/tests, native build recipe,
Android ptrace/exec patches and talloc source/config. Adaptation list and licenses are in
THIRD_PARTY_NOTICES.md. PRoot/shmem sources are pinned build inputs in dependencies.lock.json;
build copies receive patches, reference/dependency checkouts stay unchanged.
Rikkahub b10c168 and Kelivo a1741e6: inspected rootfs staging, clean guest environment,
loader/PRoot arguments and CA bootstrap; our small launcher/controller is original code.
June's existing connected groups are reused for environment cards. Haven's local-Linux
workflow was studied for separation of environments/sessions; none of its AGPL code copied.

## Workspace redesign and tools (PR 2)

Inspected June's JuneConnectedList.kt (group shapes and spacing), Rikkahub's
WorkspaceDetailPage.kt (file actions and page hierarchy), Kelivo's
workspace_file_navigation.dart (directory/terminal handoff), Haven's TerminalScreen.kt
(compact session switching and keyboard-visible controls, study only), and Horus's
GuestFileBrowser.kt and tests (bounded lstat operations). Existing June-derived
ConnectedGroup remains the only reused UI implementation. New workspace, file and
backup code is original; no Haven code or reference build dependency was added.
The bundled termlib Terminal.kt, TerminalEmulator.kt and SelectionManager.kt supply
selection, key interception, live colors, font sizing and bracketed paste. Its public
API does not expose scrollback capacity or cursor configuration, so settings disclose
those engine limits rather than injecting synthetic escape sequences.

Guest-file links resolve using guest-root semantics; traversal above guest / fails.
Browse/edit/export follow links; copy/move/delete act on final link entries and never
recurse through them. Copies and writes stage beside their destination. Existing copy
or move destinations are rejected; import explicitly confirms replacement. File work
runs in the service IO scope with cancellation, independently of navigation. All GUI
file access requires the selected environment’s sessions to be closed; admission
and blocking new sessions cover the full operation, including reads. This avoids
pathname races with a live guest without claiming PRoot is a security sandbox.
PRoot hard-link backing chains are encoded as relocatable backup entries and rebased
to the new rootfs; guest file resolution recognizes only confined backing targets.
Editor drafts stay in an Activity ViewModel across navigation/recreation and remain
open on failed or cancelled saves. Unsaved drafts do not survive Android process death.
A durable restore marker permits cleanup of abandoned staging after restart.
Backups use the versioned streaming FANDROID-BACKUP-1 format (.fatbackup), bounded to
16 GiB, 500,000 entries and depth 128. Metadata, links and POSIX permission bits are
preserved; imported identities are generated locally. Restore rejects duplicate paths,
traversal, symlink parents, truncated data and unsupported versions. Staging is promoted
only after validation. Backup is an explicit user import, not a trusted distribution image.

PR 2 sidebar/settings follow-up: inspected June SettingsScreen.kt and
AppearanceSettingsScreen.kt for grouped navigation and focused appearance controls;
checked Rikkahub drawer usages. Implemented with AndroidX Material3
ModalNavigationDrawer / PermanentNavigationDrawer / NavigationDrawerItem and existing
ConnectedGroup. No reference code was copied. Original stroked vector icons replace
ambiguous navigation shapes. Appearance has a live sample, extra keys use selection
chips, and guest fields have plain-language help. Settings disk writes run on IO.

Desktop UX study for the same follow-up: Warp's official command-palette page
(https://docs.warp.dev/terminal/command-palette), Tabby's official interface screenshots
(https://tabby.sh/), Ghostty's command-palette release notes
(https://ghostty.org/docs/install/release-notes/1-2-0), and Zed's action palette
(https://zed.dev/docs/command-palette). Adapted interaction ideas only: compact session
chrome, categorized actions and explicit keyboard hints. No desktop source, images,
branding, engine or architecture copied into the Android app. Build-free HTML visual
approximations are review aids, not Android screenshot or phone-acceptance evidence.
WezTerm's official screenshots (https://wezterm.org/index.html) were also inspected
for terminal density and keeping navigation outside the cell grid. These studies
inform our native controls rather than adding desktop panes or dependencies.

PR 2 phone-feedback follow-up: inspected actual conversation actions in Rikkahub
ConversationList.kt, ItemActionMenu.kt, SwipeToDelete.kt and ChatListSwipe.kt, plus
June's packaged phone screenshots and user-provided real Kelivo/ChatGPT screenshots.
The current Rikkahub snapshot carries AGPL-3.0, so it is UX study only here: no source
was copied or translated. Original code uses Material3 SwipeToDismissBox, contextual
DropdownMenu/DropdownMenuItem and icon-labeled sheet rows. Swipe archive hides a
service-owned session without disposing its emulator or process; restore reuses both.
Swipe close opens confirmation because process termination cannot be undone like a
chat-list deletion. Non-gesture menus expose the same actions. Archived sessions
continue to count toward limits and environment snapshot/file-access locks; archives
are live-session presentation state, not saved transcripts or process-death recovery.
The install entry remains exclusively under Environments, not the session sidebar.

The phone-feedback review also tightened identity at confirmation, archive Undo selection,
owned-session recovery for blocked file/settings screens, link-versus-target action labels,
and English/Arabic resources for the new session/tools/file controls. Tools content shares
one scrolling list for short windows; quick tabs have bounded names and a fixed New button.
These are original follow-up changes, with no additional upstream code adaptation.

## Terminal usability continuation

Studied the existing local Terax snapshot f7bee29d8690aa0afe37ed8b4c94daeb5b0211ad:
`docs/terminal.png`, `src/modules/tabs/TabBar.tsx` and
`src/modules/spaces/SpaceSwitcher.tsx`. The useful concepts are a dominant terminal,
compact contextual controls, and workspace grouping with quick tabs. Studied cmux
0fd2e596f18d918b291c6b0175d5f779b4607929's `docs/assets/screenshot.png` and
`Sources/SidebarWorkspaceRowView.swift` for stable row identity and context hierarchy.
June's local study snapshot aac069dcc86c903f8984beed2f885d46d1836607 components
and Android store screenshots informed grouped controls, selected states and
restrained chrome. Existing compiled June adaptations remain separately pinned
at f7501172211624bdfdc2690a8c6794d2483bfafb.
No source code, artwork, fonts or dependencies were copied from these studies.
The existing June-derived design-system components remain unchanged.

Original Compose changes retain settings destinations with SaveableStateHolder,
name command search explicitly, disclose the file/session restriction before entry,
and put operation stages and cancellation beside the environment they affect.
Runtime ownership, installation staging and terminal byte transport remain unchanged.

The follow-up retains the same reference-study boundary. First-use discovery,
scoped session recovery, parent-folder Back, command-search focus/clear and
bilingual settings/environment controls are original application code using
AndroidX/Material components. No additional upstream source was adapted.

## Screenshot-driven workspace refactor

The user's twelve actual Android captures exposed keyboard-obscured partial sheets,
an overloaded universal action list, and duplicated installation presentation.
Terax's compact tab/space/context hierarchy and June's focused native controls were
studied again. The implementation remains original Compose code using existing
AndroidX/Material dependencies; no reference code/assets or new runtime were added.

`CommandBrowser.kt` now owns saved-command discovery and an IME-aware dialog shared
with command composition. Session actions reuse the existing anchored action menu.
`EnvironmentScreens.kt` owns instance management and the sole detailed operation
surface; `WorkspaceSidebar.kt` focuses on sessions and exposes search on demand.
The unused legacy `EnvironmentsSheet.kt` was removed. App/session ownership, byte
transport, installation staging, environment IDs and the file/session lock remain.

## Reference refinement

Studied the new user-supplied screenshot collection and Server Box's local
`lib/view/page/setting/entries/linux.dart`, `lib/view/page/setting/entries/ssh.dart`,
`lib/view/widget/rootfs_install.dart`, `lib/data/terminal/terminal_source.dart`
and `lib/view/page/storage/file_browser_navigation.dart` for release disclosure,
device/guest distinction, instance management, shell configuration and path truthfulness.
Server Box is AGPL: original Compose UI only, no code translation/copy or build dependency.
Reused existing Material components and the already recorded June-derived ConnectedGroup.
The reference snapshots and screenshots were left unchanged. No new upstream adaptation
or distributed asset was introduced.

## Daily terminal input and navigation

Inspected the local Terax TabBar/SpaceSwitcher and cmux sidebar row for explicit
session switching versus creation; JuneConnectedList for restrained grouped native
controls; Server Box terminal_source.dart, settings/entries/linux.dart,
rootfs_install.dart and file_browser_navigation.dart for device/guest identity and
persistent environment versus session actions. Server Box remains AGPL study only.
Inspected all six supplied screenshot collections as contact sheets, and sampled
DeepSeek/Termux recording sequences. Continuous video playback was not visually
observable in this session; sampled frames cannot establish motion quality.
TermuxTerminalViewClient.onScale and the pinned termlib Terminal.kt were inspected:
the latter's existing pinch is temporary layer magnification, so the host now
handles two-pointer font sizing before the engine gesture recognizer. No upstream
code or media was copied, translated, or changed; existing licenses/pins stay intact.

Original Compose changes add an edge-only session navigator using Compose gestures,
layer-only translation/scale/corners, cancellation and Back/Escape. The terminal is
still measured at its actual viewport size throughout navigation. A temporary
native text-input focus sink prevents typing into a covered terminal while leaving
keyboard visibility under normal platform control. Source inspection cannot prove
IME continuity on a phone. The service still owns the emulator and process.
Font sizing is bounded to 8–28 sp, updates the existing engine's real font/grid,
and saves the device-wide setting after release. Text-menu +/- is the accessible
alternative. Settings synchronize external font changes only when no draft differs.
Quick controls add optional symbols via the engine's character dispatch (including
modifiers), direct customization, and animated key disclosure. Existing environment
sessions are selectable separately from creating a new process; file locks remain.

## Phone-feedback redesign and authorized distributions

The user's PR 8 phone recording exposed a keyboard-focus regression and ineffective
edge-only opening. The replacement uses full-width Initial-pass horizontal gesture
recognition, removes the hidden editable focus sink, and disables the covered input
view. Contextual session menus replace conflicting row swipes. Current proof limits
and source decisions are in docs/redesign-review.md.

The same June-inspired connected vocabulary now uses explicit group borders, nested
selected session surfaces, original geometric distribution/context vectors and a
blue/slate Material light/dark palette. These are original app assets and Kotlin;
no additional upstream source or media is adapted. Host Compose renders were produced
with a temporary local screenshot configuration that is not shipped.

Ubuntu/Alpine images are obtained directly from their official release servers;
LinuxImages.kt pins formats, sizes and SHA-256. Existing Horus-derived safe archive
extraction and the packaged PRoot runtime remain the integration. No additional
reference checkout, installer CLI, AGPL code or host Python runtime is included.

## Terax palette and restrained native chrome

Terax Default is defined by src/styles/globals.css, not by its empty default-theme
variants. At local snapshot f7bee29d8690aa0afe37ed8b4c94daeb5b0211ad, its neutral
light/dark and ANSI palette data are adapted into TerminalTheme.kt and the terminal
adapter. The native role mapping and layout remain original. Palette provenance is
recorded in dependencies.lock.json and notices under Apache-2.0; no React components,
artwork, fonts, reference build scripts or new runtime dependency are imported.

June's supplied settings/general images and existing connected-group source informed
border-free connected rows and plain leading icons. DeepSeek's supplied 0–8 second
sequence was inspected as sampled progression, not claimed as continuous playback.
The drawer's exposed corners are clipped; terminal chrome no longer has outlined
frames and keycaps. Existing gesture/input ownership is retained. See docs/design.md.

## Daily Linux workflows

Inspected the existing fandroid-tools apt/apk bootstrap, environment/backup tests,
and local proot-distro login/proot_cmd.py and login/env.py for argv, lifecycle and
clean guest context boundaries. Existing launch/backup/file components are reused;
package capture, profiles, launchers and workspace backup are original code.
No reference source or new dependency is copied. Verified apk latest-only search
semantics against upstream apk-tools v2.14.9 doc/apk-search.8.scd:
https://github.com/alpinelinux/apk-tools/blob/v2.14.9/doc/apk-search.8.scd.

The launcher installation flow directly reuses the already packaged fandroid-tools
OpenCode installer and its existing version/checksum pin; no downloaded script is
executed by the UI. The service runs the packaged script in the selected glibc guest
after prerequisite simulation and explicit confirmation. No new source adaptation.

## Softer settings and package browsing

At June aac069dcc86c903f8984beed2f885d46d1836607, inspected AppearanceSettingsScreen,
ThemeCustomizationSheet, ThemePanel, ColorSwatch and PaletteCard. Adapted the small
ColorSwatch/PaletteCard implementations into app ThemeSwatches.kt (GPLv3): local icons,
48 dp targets and selected semantics; journal/media extraction state is excluded.
The grouped theme editor, wallpaper/true-black choices, hue/chroma controls and tonal
roles use June’s pinned MaterialKolor 4.1.1 palette engine, including all palette styles;
the preference/editor ownership is original app code using Material3. The earlier connected-group adaptation
keeps its original revision. The terminal still uses its separately recorded Terax palette.

Package summaries come from apt-cache search or apk search, installed sizes from
dpkg-query or Alpine's installed database; candidate download/install sizes are read
on demand from matching apt-cache control records. Sizes are package metadata, not a
promise of total transaction disk use. Missing metadata remains unavailable. Consulted
the official Ubuntu dpkg-query manual for structured show formats. No package-manager
source was copied. UI operations still use the service-owned execution/session lock.
## Quick controls, package catalog and file browsing

Studied local Termux 8629e632fcb95da272221be327db653fb24befe9
`ExtraKeysConstants.java`, `ExtraKeysView.java` and
`TermuxDocumentsProvider.java` for seven-column extra keys, modifiers and SAF
contracts, plus June aac069dcc86c903f8984beed2f885d46d1836607's connected list for settings hierarchy. These are
original Compose/provider changes; no additional upstream code is copied. The
existing dependency pins and notices remain unchanged.

The quick-key preference now orders the actual rows (seven keys per row), rather
than silently separating Ctrl/Alt/Esc. Saved layouts remain intact. The default
matches the user's Termux reference; the editor provides select/move/remove and
add controls. The terminal viewport is never scaled for page transitions; page
entry uses alpha only, and lazy file/package rows animate placement with stable
identities. Package description filtering runs on Default with cancellable query
coalescing rather than repeatedly filtering the catalog during composition.

Package descriptions come from `apt-cache search .` and `apk search -v`; versions
and installed state still come from structured package-manager listings. Selected
install, upgrade and removal operations simulate before confirmation, with review
identity including the operation as well as names. Repository indexes must first
be refreshed to discover packages absent from a minimal image's local cache.

The read-only Android DocumentsProvider binds the service per request and reuses
file-operation admission. It does not browse a live guest or follow guest links.
Opened files are unlinked snapshots capped at 64 MiB; larger files use the existing
streaming Export action. External directory handling depends on the installed
file manager; the Android document picker is the fallback. The user grants access
through Android; there is no public filesystem path or write grant.

PR 11 was rebased onto merged PR 10 (78bb7d49). Its theme editor, palette
preferences/backups, stepwise navigation, package cards, density, sorting, sizes
and on-demand details remain. The existing drag/tap QuickControlsEditor is adapted
to seven-column rows and cross-row moves; the duplicate QuickKeyEditor is removed.
Selected update/removal and background filtering extend PR 10's package screen.

## Phone follow-up: two key rows and Terminal & tools

User phone captures and the first requested Sol high feedback round exposed the
regression from a persistent/expanded dock to variable chunked rows. Original
QuickKeyLayout now stores two rows using a separate `::` token (literal pipe is
still a supported key); legacy layouts migrate without losing keys. Each row is
one 48 dp strip with 56 dp key cells and horizontal overflow, independent of key
count. Removed keys remain available in the editor, not in the runtime dock.

Terminal & tools groups startup, actual home/start paths, individual saved-command
editing, Linux tool entry points and folded keyboard help. Appearance and extra
keys remain dedicated editors. Existing raw shell/directory default overrides
are retained rather than pinning inherited values when packages are saved.

Removed the route opacity reset and nested settings Crossfade/size animation;
these deliberately introduced transparent frames. This is source evidence for a
flash cause, not a claim of phone frame-rate measurement. The current Android
HOME/cwd is context.filesDir; Linux HOME is /root with a separate startDirectory.
No home directory is moved and no user files are migrated.

Inspected the Android DocumentsProvider findDocumentPath contract after the phone
UnsupportedOperationException. The provider now supplies confined canonical
ancestry, including the requested tree root. Existing service admission, read-only
snapshots and link refusal remain. Files uses one action strip and distinguishes
loading, true empty, filtered empty and error. No new upstream code was copied.

The second Sol high round found stale editor selections after restore, index-based
command-dialog writes after a concurrent restore, disagreement with inherited
startup resolution, and the missing storage-usage overflow action. Fixed all four:
selection membership guards, explicit keep-as-new command recovery, shared
EnvironmentStartup resolution through admitted IO, and storage results in a dialog.
The resolved directory can be refreshed without launching a process. Old directory
callbacks cannot overwrite the newly selected environment’s result.

The third/final Sol high round found two remaining external-browser edge cases:
noncanonical guest paths and missing picker MIME types. The external handoff now
canonicalizes through GuestFiles under service admission before constructing IDs,
and resolves missing MIME types on IO before launching a viewer. The provider
still refuses link IDs and confined ancestry tests include dot-path handoff cases.
No fourth review was started. These source/host checks do not verify a phone's
file-manager compatibility, IME continuity, dragging or frame timing.

## Phone gesture/input refinement

Inspected the pinned termlib Terminal.kt resizeSuspended implementation, ImeInputView.kt
focus/show/hide lifecycle and test-app ShellScreen.kt viewport settling at
7805c30f897ecf09b6b3bb5b37d92d062253ac82. The app adapter calls its existing public
resize suspension API during IME animation and window focus loss; no upstream source
was copied or changed and the dependency pin remains unchanged. Termux’s existing
TermuxTerminalViewClient keyboard toggle was inspected for explicit user intent.
The row selector/editor, terminal-viewport gesture arbitration and optional Termux key ordering use original
app code and standard Material/Compose components. No new vendored code or dependency.
