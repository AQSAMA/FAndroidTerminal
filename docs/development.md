# Development

## Reproducible tools

- JDK 17 (JAVA_HOME must point to a JDK, not only a JRE)
- Gradle 9.8.1 via checked-in wrapper and distribution SHA-256
- AGP 9.4.1 with built-in Kotlin, explicitly upgraded KGP/Compose compiler 2.4.20
- Android platform `platforms;android-37.0`, build-tools 37.0.0
- NDK 28.2.13676358, CMake 3.22.1
- Material 3 1.5.0-beta01 explicitly enables public Expressive APIs (stable 1.4.0 hides them)
- compile SDK 37, target SDK 36, min SDK 29

AGP built-in Kotlin is used; do not apply kotlin-android alongside it. Native link alignment
is 16 KiB. Packaged transitive native libraries must also be verified for page-size support.

With Android SDK command-line tools installed:

```sh
sdkmanager 'platforms;android-37.0' 'build-tools;37.0.0' 'ndk;28.2.13676358' 'cmake;3.22.1' 'platform-tools'
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-17
scripts/bootstrap.sh
./gradlew :core:runtime:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assemblePreview
```

Use local.properties for a machine-specific sdk.dir if preferred. Do not commit it. Ordinary
builds use `scripts/bootstrap.sh` to fetch the exact termlib commit recorded in
`dependencies.lock.json` into the ignored `third_party/termlib/` directory. The script
uses a shallow fetch and leaves a matching clean checkout alone. Study sources under
`references/` are local-only source snapshots of their upstream default branches without Git history.
They are not required for compilation, downloaded by CI, or catalogued in tracked files.
Fetch and inspect an upstream source before adapting it; record actual reuse in
`docs/reuse-map.md` and `dependencies.lock.json`.

## Checks

```sh
python3 scripts/check_repository.py
scripts/test-native-host.sh
```

The native host check compiles the same JNI implementation and checks actual terminal
creation, dimensions, resizing, Ctrl-C/job control, and exit status. It requires Linux,
cc and a JDK. It does not test Android SELinux, lifecycle policy or IME.

On a connected Android device or emulator:

```sh
./gradlew :core:runtime:connectedDebugAndroidTest :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Manual acceptance: new/close/switch sessions, keyboard show/hide, Ctrl-C, Ctrl-D, Tab,
hardware keys, paste (including bracketed paste), scrolling, selection, Arabic/Latin mixed
text, rotation, Activity recreation, foreground/background, process exit and notification
Stop. Device tests do not establish physical-device latency, thermal or battery behavior.

## Versions

For local builds versionCode is 1. CI uses 1000 + Android CI's run number; rerunning a run
keeps that code (Android permits a same-code signed replacement). Keep the workflow's
identity or preserve its counter when replacing it. VERSION_CODE allows an explicit positive
code (max 2,100,000,000); production publishing checks against existing release assets.
BUILD_COMMIT is the built revision; BUILD_PR identifies the pull request. Distinguish tested
merge SHA from PR head SHA in build.json. No dependency ranges or unpinned GitHub Actions.

### Execution/workspace phone acceptance

The host native tests cover real PTY behavior plus mount-target symlink refusal and
shared/exclusive ownership leases. They do not execute an Android root chroot.
Use the Preview APK on a phone; no local emulator is required or launched.

1. Confirm Esc/Ctrl/Alt/four arrows remain visible with the IME open/closed; edit
   both rows in Settings, rotate and reconnect to the existing service sessions.
2. Tap New Session in Android and Linux: confirm mode/environment inheritance.
   Long-press to explicitly choose App UID, Shizuku, Root, PRoot or root-chroot.
   Deny authorization first, then grant it; verify `id` and the header agree.
3. With Shizuku started under ADB, verify UID 2000, Ctrl-C, resize, close, service
   disconnect and Activity recreation. Shizuku started as root must report UID 0.
4. On a rooted phone, keep SELinux enforcing. Open two root-chroot sessions in one
   environment; verify `id -u`, `/proc`, `/dev/pts`, package install and `/root`
   persistence. Close one and keep the other working. Close the last, then open
   PRoot and Files. Force-stop once; if cleanup was interrupted, recover ownership
   in Working mode. Check no guest mount remains in Android's mount namespace.
5. In Working mode grant shared-storage access and create the named shared folder.
   Enable only Android and one environment; verify each sees its expected path.
   Leave Start off: HOME/pwd must keep their normal values. Turn Start on: new
   sessions start in the shared folder; existing sessions keep running. Disable
   and reopen: the prior start directory returns and shared files remain intact.
6. Revoke storage access in Android settings. A new opted-in session must give a
   recoverable error. Restore access or disable the integration; verify recovery.
7. From Linux use the explicit Android command shown in Working mode. Check `id`
   reports App UID from PRoot and real UID 0 from root-chroot. Guest libraries and
   PATH must not leak into Android commands.
