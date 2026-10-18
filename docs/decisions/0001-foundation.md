# 0001: Native foundation with separate transport and emulation

Status: accepted for the first implementation, 2026-10-18.

Use ConnectBot termlib at 7805c30f897ecf09b6b3bb5b37d92d062253ac82 as an Android/Compose component and Termux's PTY implementation
as a small vendored dependency. Do not fork an entire terminal application as our product
skeleton. Keep Ecto/Ghostty as the main performance/protocol upgrade candidate, not an
assumption that libghostty-vt includes the desktop renderer.

Use a modern compile SDK and target 36. Android shell and packaged native libraries execute
without downloading a private-data shell executable. Runtime executable policies and future
standalone package-prefix compatibility are explicit later checks.

The foreground service owns sessions in the same app process initially. This preserves
Activity-independent lifetimes without inventing an IPC terminal-frame protocol. It does
not promise survival of process death. Add process separation only for a demonstrated need.

Use GPL-3.0-only for original app code; preserve upstream exceptions/notices. No Haven AGPL
or Yetty BSL source is imported by this decision.

The initial published 0.3.13 artifact lacks an explicit TerminalEmulator.close API. The
selected later source commit implements deterministic lifetime release, so it is built as
a read-only third_party submodule with our minimal Gradle script. Only its main sources
and CMake build are consumed, not upstream publishing/coverage/docs plugins. A future
released artifact can replace this arrangement after matching lifecycle tests pass.
