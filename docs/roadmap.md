# Vertical slices

## Foundation (this PR)

Android shell -> real PTY -> termlib -> Compose. Tabs, keyboard controls, foreground lifetime,
explicit permission context, build information, source references, CI and preview infrastructure.
Accept when host checks, JVM checks, Android build/lint and device lifecycle/input checks pass.

## SSH and existing Termux

One maintained SSH stack, host-key verification, keys, shell/exec/SFTP, connection
profiles and transfer cancellation. Connect to existing Termux via authenticated loopback SSH.
Use RUN_COMMAND only for supported setup/short commands; don't attach arbitrary original tabs.

## Standalone Linux

Embed PRoot+loader and Debian ARM64 rootfs installation with pinned digest, safe extraction,
atomic promotion, cancellation, progress, distro lifecycle and recovery from interrupted setup.
Reuse inspected upstream implementations rather than reimplementing extraction/orchestration.

## Privilege and graphical operations

Authorized root and shell services, rooted chroot and structured operations. Every action
reports actual context, capabilities, exit status and relevant output. File providers retain
local/SAF/SFTP semantics. No blanket claim that root equals an unrestricted Linux kernel.

## Native environment and engine evaluation

Build a standalone Termux-compatible bootstrap and initial package closure for this app's
prefix. Test modern-target execution before expanding the package repository.
Evaluate alternative engines against termlib on the same input traces and physical device. Compare
compatibility, IME, selection, Arabic, sustained content lag, frame deadlines, memory and power.
Choose an engine based on measured behavior, not desktop benchmark claims or GPU branding.
