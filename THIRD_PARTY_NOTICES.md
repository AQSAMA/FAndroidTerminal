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
Evans, MIT license. Unmodified fetched source dependency; source revision
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

## Linux runtime and installer

PRoot: https://github.com/termux/proot at 7266fb3e8516535682f5a9c8f3a7e70f6506eddb,
GPL-2.0-or-later (compatible with this GPLv3 application); see licenses/proot-COPYING.txt.
libandroid-shmem: https://github.com/termux/libandroid-shmem at
7f0bd7e25dbdd146265aff7c6a890029e374622d, BSD; see licenses/libandroid-shmem.txt.
Both are fetched at pinned revisions and compiled from source, never downloaded as runtime executables.

Horus: https://github.com/scarif-labs/horus at 02d6fdd495c3d1959054ac4f9200a7fe5ce6edaa,
Copyright (c) 2026 0x1379, MIT; see licenses/Horus-MIT.txt.
Adapted SafeTarExtractor, TarFixture, extractor tests, native build script and Android PRoot
patch. Changes: app package/API, cooperative cancellation, tar checksum/truncation checks,
hardlink byte budget/modes, symlink-safe deletion, build paths, relocatable shmem temp path.
Talloc 2.4.3 core/config from Horus native/talloc retains its original Samba copyrights and
LGPL-3.0-or-later license in third_party/talloc and licenses/talloc-LGPL-3.0.txt.
PRoot's GPL source patch is patches/proot-android.patch. Native binaries are 16 KiB aligned.
XZ for Java 1.10: https://tukaani.org/xz/java.html, 0BSD.

Debian rootfs is downloaded separately, not bundled in the APK. Image provenance and
hashes: docs/phone-acceptance.md. Debian packages retain their own /usr/share/doc/*/copyright
and source repository rights. No Haven or Yetty code is incorporated.
