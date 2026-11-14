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
