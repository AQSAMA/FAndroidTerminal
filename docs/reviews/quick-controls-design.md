# Quick controls and settings design loop

Scope: drag ordering/cross-row movement, real dock preview, and the user-supplied
Extra keyboard keys, Working mode and Terminal & tools screens. Required review
categories: visual quality, task usability, functional completeness/reliability.

Baseline: four user phone screenshots supplied with the request. Existing native
Material components, June's local connected-list reference and the application's
runtime dock informed the implementation. No new visual system or dependency.

One independent clean-context reviewer inspected base
`6f769183b1a1edf6333f6eaa9c3adb0f4781187c` plus the implementation diff. Its final
review included fixes to duplicate imports, disabled session-only preview actions
and physical-coordinate RTL placement. Source walkthroughs covered reorder,
cross-row/empty-row moves, cancellation, add/remove/presets, draft/save/runtime
layout sharing, preview expansion, authorization/storage/recovery, directories
and saved commands. No source-level delivery blocker was found.

Findings and disposition:

- Duplicate imports: fixed; initial compilation failure is superseded by the
  final build checks reported in the PR.
- Preview menu callbacks without a session: disabled while retaining the same
  runtime component, interactive expansion and modifier feedback.
- RTL drag-overlay placement: uses absolute coordinates.
- Authorization/recovery messages after the shared-workspace list: moved beside
  the access section, with accessible live-region feedback. This final small fix
  was self-reviewed and built, not reviewed by a second critic. Final short
  provider action labels and a horizontally scrollable, single-line path display
  also received self-review only.
- No drag edge auto-scroll: deferred. Short windows/large fonts can put the
  destination offscreen; tap a key and use Move to other row, then Previous/Next.

Visual quality remains unscored: no post-change Android captures were available.
Task usability and functional reliability have source/test evidence but remain
unscored pending phone acceptance. No emulator or device was launched. Stop after
one review because missing phone evidence prevents visual acceptance; further
source-only rounds cannot establish it.

Phone checklist (Preview APK):

1. Hold a grip; drag to both sides of another key, across rows, and into an empty
   row. Release outside both rows to cancel. Confirm no duplicates or lost keys.
2. Expand/collapse the preview arrow; compare it with the actual terminal after
   Save. Remove/add/preset, Save, leave/reopen and rotate to verify ordering.
3. Try Arabic UI, large system text and a short/landscape window. Use the explicit
   move/order buttons where the drag destination is offscreen.
4. Check root unavailable, Shizuku denial/authorization, storage grant return and
   recovery feedback. Confirm directory copy/details and saved-command editing
   remain reachable and readable.
