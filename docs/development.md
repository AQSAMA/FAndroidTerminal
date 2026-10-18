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
scripts/bootstrap.sh --references core
./gradlew :core:runtime:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assemblePreview
```

Use local.properties for a machine-specific sdk.dir if preferred. Do not commit it. Ordinary
builds require only `git submodule update --init --depth 1 -- third_party/termlib`; other
reference sources are optional for compilation. Agents should fetch actual references before adapting code.

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
