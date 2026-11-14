# Workspace redesign after phone feedback

Evidence baseline: PR 8 b35e3761 and the user's actual 35-second phone recording
52021.mp4. The supplied clip was inspected as sequential sampled frames, not claimed
as continuous playback. It shows the unwanted keyboard on navigator opening,
attempted swipes and weak section separation. Earlier reference collections remain
unchanged. This revision replaces the earlier narrow edge gesture/focus sink.

## Changes

- Full-width single-finger horizontal opening is recognized in Initial before the
  terminal recognizer. Vertical gestures, active selection and multi-touch remain
  with the terminal. Cancellation and Back retain their own state paths. Sidebar
  row swipe-to-archive/close is removed in favor of explicit contextual actions, so
  horizontal motion has one meaning and cannot stop a process accidentally.
- No hidden text field, forced terminal refocus or automatic keyboard on new session.
  Committing the navigator open clears focus and hides the IME; the terminal's input view
  is disabled while covered. Only terminal taps and keyboard controls ask for IME.
- Blue/slate native light and dark themes, new geometric Android/distribution and
  action vectors, tinted context badges, bordered environment groups, nested session
  rows and stronger selection. Header, terminal viewport and input dock are rounded
  independent surfaces. Settings use bordered connected groups and icon tiles.
- Ubuntu and Alpine can be selected, named and installed with actual pinned official
  images. Each instance keeps distribution, version, shell, folder and private rootfs
  identity across settings, restart and backup/restore. Guest settings expose the
  exact app-private host path separately from guest shell and working directory.
- Focus mode and its menu/shortcut behavior are removed. No reference code, brand
  assets, snapshots or unrelated account/AI features were copied.

## Proof and limitations

Host tests verified the downloaded official Ubuntu/Alpine SHA-256 and byte counts,
extracted the real archives through SafeTarExtractor, resolved executable guest
shells and confirmed ARM64 ELF architecture and os-release. Metadata migration and
Alpine backup/restore retain distribution, release, shell and files. CI uses small
network-free fixtures; the explicit local official-archive test is optional there.

Device proof was attempted through T3 Device panel under Full stop testing permission.
The existing Curiosity API35 emulator booted with Android SystemUI ANR. The prescribed
recovery attempt also failed. Device testing was stopped and this task's device was
shut down/removed; no other session was used. This is infrastructure failure, not a
passing app test. Current phone gesture, IME, PRoot install and TalkBack acceptance
remain pending. Static host renders, when supplied, are actual Compose component
renders labeled as host evidence; they cannot prove device runtime behavior.

## Sources for added images

- Ubuntu: https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/SHA256SUMS
- Alpine: https://dl-cdn.alpinelinux.org/alpine/v3.22/releases/aarch64/alpine-minirootfs-3.22.8-aarch64.tar.gz.sha256

Exact URLs/digests/sizes are in LinuxImages.kt. Downloaded archives remain outside Git.

## Phone checks

1. Swipe from the middle of the terminal horizontally; drag back, cancel and Back.
   Try while IME was closed and open; opening the navigator must not summon IME.
   Verify vertical scroll and two-finger font resize still work, in English/Arabic.
2. Compare sidebar groups, selected session, header and settings at large text size.
   Open each environment's settings and inspect host rootfs versus guest paths.
3. Install Ubuntu and Alpine; check version, `uname -m`, package manager, DNS/TLS,
   close/reopen, create files, close sessions, browse/edit/export/import files.
4. Export/restore each distro and confirm identity/shell/files. Import an older
   Debian backup. Cancel/retry downloads and ensure existing environments survive.

The inspected host Compose renders cover English/Arabic sidebar and light/dark
settings plus multi-distribution environments. Fixtures are explicitly sample data,
not installed/running environments. The renderer configuration and fixtures were
removed afterward. Real-archive host tests all passed (3 cases, including metadata
migration and backup). A first lint pass caught indentation after removal of the
swipe wrapper; corrected before final verification.

## Independent review outcome

Requested fresh critics: Codex gpt-6.1-sol/high and gpt-6.1-terra/high. Critic A
failed because the provider usage limit was reached; no substitute was used and
no approval is claimed. Critic B returned CHANGES: B-01, a first-finger horizontal
drag followed by a second pointer could clear focus/hide IME before cancelling.
The fix separates visual drag progress from committed modal input blocking, and
a regression scenario checks that this staggered gesture retains input focus.
The regression is compiled only; device execution remains pending.

Critic B's host-render visual score was 7/10 and provisional usability 6/10; no
reliability score was justified. These are not phone or motion acceptance scores.
No fresh review after the small B-01 fix has been obtained.
