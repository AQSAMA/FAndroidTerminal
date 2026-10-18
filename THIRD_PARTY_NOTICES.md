# Third-party notices

Original FAndroidTerminal code: GPL-3.0-only; see LICENSE.

## Termux PTY

Source: https://github.com/termux/termux-app/tree/8629e632fcb95da272221be327db653fb24befe9
Files: terminal-emulator/src/main/jni/termux.c and src/main/java/com/termux/terminal/JNI.java.
The Termux repository declares GPLv3-only with Apache-2.0 exceptions for Android Terminal
Emulator code in terminal-emulator/terminal-view. Original repository notice is preserved in
third_party/termux-pty/UPSTREAM-LICENSE.md; GPL and Apache texts are distributed here.
Changes on 2026-10-18: make JNI class public for the adapter; retry interrupted waitpid and
return a defined failure status. Native symbols and PTY construction are otherwise retained.
See third_party/termux-pty/README.md and patches/termux-pty.patch.

## ConnectBot Terminal source snapshot

Copyright 2025 Kenny Root and contributors. Apache License 2.0. Includes libvterm by Paul
Evans, MIT license. Unmodified source module; source revision
7805c30f897ecf09b6b3bb5b37d92d062253ac82. Dependency notice and source headers
are retained in the upstream source. See licenses/Apache-2.0.txt and licenses/libvterm-MIT.txt.

## June

Source: https://github.com/DenserMeerkat/June/tree/f7501172211624bdfdc2690a8c6794d2483bfafb
GPLv3. JuneConnectedList.kt and JuneFloatingActionBar.kt adapted into ConnectedGroup.kt.
Changes: app package/names, reduced parameter surface, application-specific shapes/colors.
The existing copyright holders retain their rights; this attribution does not claim authorship.

## Android, Kotlin and libraries

AndroidX/Compose and Material Components: Apache-2.0. Kotlin and kotlinx.coroutines:
Apache-2.0. Gradle Wrapper: Apache-2.0. These are pinned build/runtime dependencies, not
relicensed as exclusive app-owned code. Their notices remain in distributed dependencies.

Haven and Yetty are references only; no source from either is included in this application's
build. Other reference submodules retain their own licenses and are not compiled automatically.
