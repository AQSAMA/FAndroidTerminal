# 0002: Standalone Linux first, with Kotlin environment management

Status: accepted, 2026-11-12.

## Decision

The next product slice is a downloadable Debian ARM64 userland running through packaged
PRoot and its loader, managed by native Kotlin code in the existing application. Keep
Android shell sessions working. Existing-Termux integration and a Termux-style native
package environment are later, independent backends rather than prerequisites.

A rootfs is the distribution's files. PRoot supplies the unprivileged execution layer.
The proot-distro CLI is a source of implementation evidence; embedding its full Python
application or providing a host Python runtime is not required. Adapt inspected upstream
solutions where they reduce implementation and maintenance cost. Keep the existing
terminal engine until a measured compatibility or performance need justifies replacing it.

## Scope and boundaries

- Identify each installed environment separately from the distribution/version and its
  running sessions. Multiple instances can use the same image without sharing mutable
  rootfs or home directories accidentally.
- Keep environment files and settings persistent, while the foreground service owns live
  PTY sessions. Preserve those sessions across tab switches and Activity recreation;
  process death still ends running processes.
- Pin shipped runtime inputs and rootfs digests. Download and extract into staging,
  probe the installed guest, then promote it. Report progress and support cancellation
  and recovery without overwriting a working environment or deleting user projects.
- Manage the needed DNS, certificates, guest environment and explicit shared paths. Use
  structured execution results for graphical operations, separate from interactive VT data.
- Preserve transport boundaries so a later native-prefix or remote backend can reuse the
  terminal UI. Do not build an abstract backend framework before a second implementation
  needs it; installed Linux packages are not interchangeable with native Android packages.
- PRoot shares Android's kernel and UID constraints. It does not grant real root,
  namespaces, full init-system support or a security boundary between guest environments.

## Delivery and acceptance

Deliver a complete install -> open terminal -> persist files -> reopen workflow, with
clear failure/retry/cancel states, followed by management of additional instances and
images. Reuse existing Compose/Material components and inspected local references.

Use focused host/JVM tests for installer failure modes and ownership. GitHub Actions
builds the Preview APK; the user tests it on a phone. Do not launch a local emulator.
Python, package installation and OpenCode CLI are concrete compatibility targets for
phone acceptance, not functionality already verified by this decision. Record the exact
image, program versions and results; test tooling must not imply that every package works.

## Authorized extension: Ubuntu and Alpine (2026-11-14)

The user explicitly authorized downloadable Ubuntu and Alpine alongside Debian.
Keep the same packaged PRoot and service/session ownership. The small pinned ARM64
catalog adds Ubuntu Base 24.04.5 (glibc, apt, bash) and Alpine minirootfs 3.22.8
(musl, apk, sh), with their official HTTPS URLs, exact byte counts and SHA-256.
Distribution and release are stored independently of instance names; old records
remain Debian 13.7. The installer chooses compression, space requirement and
post-install package/architecture probes from this catalog. Alpine never receives
APT configuration, and the glibc-only pinned OpenCode helper explicitly refuses
Alpine instead of pretending compatibility.

Backup version 2 records distribution/release. Import still reads version 1 as
legacy Debian. Rootfs, guest settings, graphical file locks, cancellation, verified
staging/promotion and existing user files retain their earlier boundaries. This
adds userlands, not Android root or kernel isolation. Actual ARM64 execution remains
phone acceptance; host archive extraction does not establish Android PRoot policy.
