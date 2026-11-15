# UI and flow review — soft workspace

Requested: three sequential fresh GPT-6.1 Sol reviews at high reasoning effort,
focused on UI/UX and user flows. Each review uses current source and host Compose
renders. Host images are static layout evidence, not phone/gesture acceptance.

## Round 1

Verified and fixed:
- Guest default tabs discarded drafts: SaveableStateHolder now retains each target.
- Restore incorrectly validated export encryption before choosing a file: restore now
  chooses a file first and asks for its password only for an encrypted header. Invalid
  passwords can be retried; no data is promoted before full validation.
- Startup drafts lacked index feedback: they now use the same unsaved row/notice.

The reported overlapping theme sliders came from a non-scrolling, height-constrained
review fixture. Replaced it with the actual scrollable Appearance page for inspection.
This does not establish phone scrolling/motion behavior.

## Phone acceptance

- In Appearance, try color and palette tabs, sliders, wallpaper colors, true black,
  Save, return and reopen; check light/dark and Arabic text at large font scales.
- Drag quick keys within either row; also tap source/destination, remove/add, Save,
  and check the actual terminal order. Check TalkBack move actions.
- Back from a setting returns to Settings; Back through environment packages returns
  to that environment; repeated Back at the terminal leaves the app visible. Check IME.
- Files opens Files with active sessions; its separate recovery action manages them.
- Load/refresh Debian/Ubuntu/Alpine catalogs, search descriptions, sort A–Z/Z–A/size,
  filter installed/updates, open details, select and review/install. Check cancellation.
- Edit guest defaults, switch distribution and return: draft stays. Save explicitly.
- Restore plaintext and encrypted workspace backups without changing export controls;
  retry a wrong password, cancel, and verify original data remains intact.

## Round 2

Fresh reviewer confirmed all three round-1 fixes. Verified and fixed:
- Expanding cached package rows tried to run a guest command and hit the session
  lock: expansion is now always local, with an explicit metadata-load action only
  when unlocked. The existing session-management action explains the lock.
- Guest-default scope was unclear after removing prose: the page now says “New
  environment defaults” and links directly to managing installed packages.

Parent flow check also preserved environment identity and package target/query in
Back history; opening the terminal from navigation clears previous page history.

## Round 3

Fresh final reviewer judged the result ready for Preview with two bounded issues.
Verified and fixed both:
- Backup unlock/review modals now expose active cancellation while busy, and review
  shows progress inside the modal instead of obscuring the only operation controls.
- Clean settings drafts now synchronize to changed saved preferences (including backup
  restore). Truly edited sections stay intact, show a conflict notice and offer “Use
  saved settings”; saving remains an explicit replacement decision.

All three reviews used fresh GPT-6.1 Sol agents with high reasoning effort. No claim
of phone verification is made. The parent checked findings against source and implemented
fixes sequentially; final small fixes receive focused compilation/regression checks,
not a fourth review round.
