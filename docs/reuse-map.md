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
runs in the service IO scope with cancellation, independently of navigation.
Backups use the versioned streaming FANDROID-BACKUP-1 format (.fatbackup), bounded to
16 GiB, 500,000 entries and depth 128. Metadata, links and POSIX permission bits are
preserved; imported identities are generated locally. Restore rejects duplicate paths,
traversal, symlink parents, truncated data and unsupported versions. Staging is promoted
only after validation. Backup is an explicit user import, not a trusted distribution image.
