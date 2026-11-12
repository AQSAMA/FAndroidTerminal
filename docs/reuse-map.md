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
