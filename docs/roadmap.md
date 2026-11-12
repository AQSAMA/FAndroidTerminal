# Vertical slices

## Working foundation

Android shell -> real PTY -> termlib -> Compose is implemented. It includes tabs,
keyboard controls, foreground session ownership, CI and Preview infrastructure.
The first Linux implementation adds downloadable Debian environments.

## PR 1: standalone Linux with native management

Follow [decision 0002](decisions/0002-linux-environments.md): package PRoot/loader and
install Debian ARM64 through a Kotlin-managed flow with a pinned digest, bounded
extraction, staging, guest verification, progress, cancellation and interrupted-install
recovery. Open the installed guest in the existing service-owned terminal.

Separate environment instances from sessions and provide durable files/settings. Then
extend management to multiple installed instances and additional images. Keep the first
slice usable end to end; do not create placeholder modules for future capabilities.
Phone acceptance targets include package installation, Python and OpenCode CLI, with
actual versions/results recorded. Preview APKs come from GitHub Actions for user testing.

## Environment tools and settings

Build on working environments: coherent terminal/input settings, environment switching,
file access, backup/restore and useful package operations. Use structured execution
results for graphical actions. Keep advanced options accessible without crowding the terminal.

## SSH and existing Termux

Add a maintained SSH stack, verified host keys, keys, shell/exec/SFTP, connection profiles
and transfer cancellation. Existing Termux can be reached through authenticated loopback
SSH; supported RUN_COMMAND setup is separate from attaching arbitrary original tabs.

## Privilege and native packages

Add authorized root/shell services or rooted chroot only in a requested slice. Keep
execution authority explicit. A standalone native package environment needs its own
bootstrap/prefix and validated modern Android execution; it is not required for rootless Linux.

## Engine evaluation when justified

Compare alternative engines with termlib only for a concrete need, using the same traces
and real phone acceptance for input, Arabic, sustained output, memory and power. Desktop
benchmarks or rendering branding alone are not reasons to replace the working engine.
