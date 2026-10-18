# Architecture

## Working foundation

`app` -> `core:sessions` -> `core:runtime` and `core:terminal`.
`app` -> `core:designsystem` and the terminal adapter.

- app: Activity, Compose navigation, tabs, key controls and build/context information.
- designsystem: a small June-derived component vocabulary and Material Expressive theme.
- terminal: the termlib surface/adapter; grid direction is independent of app RTL.
- runtime: explicit ShellSpec and SessionTransport, plus Termux-derived JNI PTY.
- sessions: foreground Service owns emulator, transport and session coroutine scope.

Normal builds consume the pinned termlib source module. They do not run its upstream
publishing, docs, coverage, or analysis plugins. The source submodule remains available to
inspect implementations and upstream tests. JNI code needed by the app is separately
vendored under third_party with recorded modifications.

## Ownership and threading

Activity starts/binds the non-exported foreground service while visible and unbinds when
stopped. START_NOT_STICKY prevents inventing replacement live sessions after process death.
Session tabs borrow emulators. Closing a tab explicitly disposes its session. A service can
survive Activity recreation and foreground/background transitions; force-stop, process death,
OOM, Doze and OEM limits are not defeated by a foreground notification.

PTY reads and process waits run on IO dispatchers. Input is queued in order; resize is sent
to the PTY. VT state is never replaced with a growing Compose string. Dispose races must
not feed a closed native emulator or leak a subprocess created after cancellation.

The notification returns to the app or closes all sessions. No automatic wake lock, root
request, unrestricted external service, terminal transcript logging or network listener.

## Execution contexts

Future backends describe transport, runtime and authority rather than proliferating one
class for every combination. SessionTransport remains byte-oriented; administrative command
execution must be a separate structured executor with argv, cwd, env, stdout, stderr and exit.
Graphical operations must not parse the rendered terminal's screen to determine success.

- Android shell: /system/bin/sh under app UID, clean environment.
- Existing Termux: authenticated loopback SSH/SFTP into Termux's UID; RUN_COMMAND for setup
  where supported. Arbitrary original Termux tabs are not exported PTYs.
- Standalone Linux: packaged Termux PRoot/loader, distro rootfs in private storage.
- Standalone native: separate bootstrap/package prefix, rebuilt using Termux's package tooling.
- Privilege: libsu RootService or Shizuku UserService with descriptor-based streams.
- Rooted Linux: chroot, capability-aware mount handling, no promise of full init/container support.

## IDs and storage

Production: io.github.aqsama.fandroidterminal
Preview: io.github.aqsama.fandroidterminal.preview
Debug: io.github.aqsama.fandroidterminal.debug

Each has a different UID/private directory. Future native packages must be built for each
supported prefix; changing PREFIX in the process environment does not relocate binary paths.
Use internal storage for runtime executables/rootfs. SAF and SFTP are stream-oriented file
providers, not automatically File objects. No persistence claim is made for in-memory shells.
