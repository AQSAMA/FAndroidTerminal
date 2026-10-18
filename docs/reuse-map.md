# Source reuse map

references.lock.json is the machine-readable source of truth. Every submodule points at an
exact commit; no branch auto-updates. Use `scripts/bootstrap.sh --references core` (six main
sources) or `all` (all fifteen). References are not included in Gradle by discovery.

## Actually used

| Component | Source | Integration |
| --- | --- | --- |
| Terminal emulation/render/input | connectbot/termlib 7805c30f897e | Unmodified source submodule with a small host-owned Gradle build script |
| PTY/JNI | termux/termux-app 8629e632fcb95da272221be327db653fb24befe9 | Two files vendored with minimal documented adaptations |
| Connected groups / floating controls | June f7501172211624bdfdc2690a8c6794d2483bfafb | Adapted into core/designsystem, provenance in source headers |

Read termlib's Terminal.kt, TerminalEmulator.kt, ImeInputView.kt, TerminalShaping.kt and
benchmark/README.md; Termux's terminal-emulator/src/main/jni/termux.c and TerminalSession.java;
June's presentation/components/JuneConnectedList.kt and JuneFloatingActionBar.kt.

## Next slices

- Horus: native runtime packaging, ProotRuntimeLocator, DistroStore, ProotSessionLauncher.
  Separate its MIT code from GPL/LGPL/native dependencies; do not transfer its React Native UI.
- proot-distro: distro/image orchestration. Existing Python runtime/packaging is an integration
  choice; do not pretend the tool is a Kotlin library or an isolation boundary.
- Ecto: terminal-compose-view/session and Ghostty JNI for a measured alternative engine.
  Its direct /data/data/com.termux bash probe is not a cross-UID bridge.
- termux-exec: system linker execution for modern-target Bionic runtime, including static ELF
  and direct-syscall limitations. This does not automatically guarantee Play approval.
- glibc-packages: optional compatibility, not a distro or kernel namespace system.
- chroot-distro: rooted distro lifecycle; validate kernel and SELinux capabilities.
- libsu/Shizuku API: RootService/UserService; keep authority explicit.
- Termix: infrastructure workflows, not a native Android execution backend.

## Restricted references

Haven is AGPLv3. Study its UX/integration and use upstream libraries directly. Do not copy
its code into GPL-only application modules without an explicit section-13-compatible
licensing decision. Yetty's own current code is BSL 1.1 with no additional use grant and a
2030-03-20 change date: architecture study only; bundled permissive components can be
obtained independently. Renaming or translating source is still adaptation.

MIT/BSD/Apache-2.0 notices remain required. GPLv2-only is not interchangeable with GPLv2-or-later.
Record file-level exceptions, fonts, images, binary provenance and build scripts before copying.
