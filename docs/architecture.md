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
publishing, docs, coverage, or analysis plugins. `scripts/bootstrap.sh` fetches the ignored
source checkout at its recorded commit for implementation and upstream-test inspection.
JNI code needed by the app is separately
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

## Next slice: standalone Linux

See [decision 0002](decisions/0002-linux-environments.md). Native Kotlin manages
rootfs acquisition, installed instances and session launch through packaged PRoot/loader.
The full proot-distro CLI and a host Python runtime are not required. An environment's
persistent files and settings are separate from the service-owned running sessions.
Termux integration and native-prefix packages remain later backends.

## Execution contexts

Future backends describe transport, runtime and authority rather than proliferating one
class for every combination. SessionTransport remains byte-oriented; administrative command
execution must be a separate structured executor with argv, cwd, env, stdout, stderr and exit.
Graphical operations must not parse the rendered terminal's screen to determine success.

- Android shell: /system/bin/sh under app UID, clean environment.
- Existing Termux: authenticated loopback SSH/SFTP into Termux's UID; RUN_COMMAND for setup
  where supported. Arbitrary original Termux tabs are not exported PTYs.
- Standalone Linux: packaged PRoot/loader, distro rootfs in private storage.
- Standalone native: separate bootstrap/package prefix, rebuilt using Termux's package tooling.
- Privilege: separate authorized root or shell services with descriptor-based streams.
- Rooted Linux: chroot, capability-aware mount handling, no promise of full init/container support.

## IDs and storage

Production: io.github.aqsama.fandroidterminal
Preview: io.github.aqsama.fandroidterminal.preview
Debug: io.github.aqsama.fandroidterminal.debug

Each has a different UID/private directory. Future native packages must be built for each
supported prefix; changing PREFIX in the process environment does not relocate binary paths.
Use internal storage for runtime executables/rootfs. SAF and SFTP are stream-oriented file
providers, not automatically File objects. No persistence claim is made for in-memory shells.

## Explicit session authority and shared storage

A terminal's ExecutionMode is immutable. Tap New Session inherits its environment
and mode; the toolbar's long-press chooses another authority without changing any
existing process. Shizuku uses its own user-service PTY and reports the service UID;
only `su` verified as UID 0 may enter the root-chroot launcher. App UID, Shizuku,
simulated PRoot root and device root remain separate identities.

Root-chroot uses a private recursive mount namespace and a private PID namespace.
PID 1 supervises the guest; its exit kills remaining guest descendants. The outer
supervisor reaps children before physically restoring rootfs ownership to the app.
No global mounts or SELinux changes are made. A durable root-access.pending marker
blocks graphical environment operations after interrupted cleanup; Working mode
can reauthorize root and repair ownership under the native ownership lock.
Multiple root-chroot sessions may share an environment. The last native lease
repairs ownership; PRoot, package tools and file operations cannot overlap them. Root programs have real device authority;
this is not a security container or full Android-compatible init system.

SharedWorkspace persists one Android grant and grants keyed by environment UUID.
Storage access is checked on every new launch. Linux receives `/shared` only for
an enabled grant; an optional startup override never rewrites the saved directory
or HOME. Running sessions retain their launch configuration. These settings are
integration controls: app UID, Shizuku and root retain whatever Android grants them.
A deliberate Linux-to-Android bridge is documented in Working mode and runs with
a cleared environment, retaining the current process authority.
