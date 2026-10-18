# FAndroidTerminal

A native Android terminal platform built with Kotlin, Jetpack Compose and Material 3 Expressive.

The foundation implements **Android shell sessions**, a real PTY, touch terminal input,
selection, paste, a configurable-size terminal surface, session tabs and a foreground
session service. UI components are adapted from June; terminal emulation uses ConnectBot
termlib; PTY creation is based on Termux.

**This shell runs as the app's Android UID.** It is not an ADB/root shell and does not
include a standalone Termux package environment yet. SSH/SFTP, an existing-Termux bridge,
PRoot distributions and privileged backends are subsequent vertical slices, not working
features hidden behind placeholder buttons.

## Try a pull request

Open its **Android CI** run and download `android-build`. It contains a temporary-signed
`bootstrap-debug.apk`, an unsigned Preview APK and build metadata. The debug APK installs
alongside Production, Preview and Termux. Its CI-generated debug signing key is temporary:
updating a debug APK from another runner can require uninstalling the old Dev build.

After maintainer signing setup and the bootstrap PR are merged, trusted same-repository
PR builds also receive a directly downloadable, stable-signed Preview APK through a
`preview-pr-N` pre-release and an updated PR comment. Preview has its own application ID
and preserves data when updating to a newer Preview build signed by the same key.

## Build

See [development](docs/development.md). Quick start:

```sh
scripts/bootstrap.sh --references core
./gradlew :app:assembleDebug
```

Android 10+; initial APKs include ARM64 and x86_64. Source, toolchain and action versions
are pinned. Only the termlib source dependency participates in Gradle; the other references do not.

## Engineering map

- [Architecture](docs/architecture.md) and [roadmap](docs/roadmap.md)
- [Source reuse and license boundaries](docs/reuse-map.md)
- [Preview, signing and Production](docs/releases.md)
- [Repository administration](docs/github-setup.md)
- [Development and verification](docs/development.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

GPL-3.0-only for this application's code, with upstream components retaining their licenses.
The source and build instructions shipped here are the corresponding-source starting point;
redistributors must keep matching source, dependency revisions and notices available.
