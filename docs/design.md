# Foundation visual direction

June is the primary component reference: connected surfaces, rounded grouped controls,
clear hierarchy, generous touch targets and action-driven motion. Adapt its component code
rather than copying journal-specific navigation or domain objects.

The terminal is the dominant work surface. App chrome follows the system light/dark theme;
the initial terminal palette remains a deliberately stable dark canvas to preserve ANSI
contrast. Core colors: Graphite terminal #1E1E24, text #E2E1E6, primary #B9CBB2, on-primary #24351F,
control surface #202328. App typography follows Material; terminal uses Android monospace
fallback. UI direction follows locale; terminal grid remains LTR.

Header identifies the environment and active session. The drawer and contextual overflow
own session actions; quick tabs appear while the IME is hidden. A compact key row sits above the IME. No dashboard full of unavailable features,
background effects, fake telemetry or motion inside the terminal cell grid. Keep actions
reachable one-handed and test narrow layouts with the keyboard visible.

## Phone refinement from the supplied reference collection

Keep the existing semantic colors, Material typography, 24 dp connected-group envelope,
12 dp session shapes and 48 dp minimum controls. The terminal is the focal surface;
settings and management remain quiet, left-aligned native lists. Accent color indicates
selection or a primary action, rather than decorating every row.

The 54 user-supplied images were opened, including all six app collections. June's
connected groups informed settings row joins and a single settings-detail header.
T3's selection controls informed non-color checkmarks; OpenCode's theme previews informed
palette swatches beside the existing live terminal sample. Keep and X reinforced
consistent labeled navigation and icon/text alignment, without adding their features.
Terax continues to lead terminal hierarchy: hide quick tabs while the IME is visible,
keep the session drawer available and retain the same selected service session.

Server Box was studied for device/Linux separation, installed-instance actions, release
information before installation, contextual shell help and file navigation. Only the
existing pinned Debian ARM64 image is offered. The installer discloses its release,
download and storage requirement, and keeps the editable instance name before contextual
help. Installed environments show their user-given name independently of the image;
we do not claim that package upgrades preserve the original image's release version.
Settings and Files keep labeled actions; blocked Files leads directly to scoped session recovery.
Files uses a review label while blocked; the recovery explanation includes archived sessions. The recovery sheet offers a distinct trash action with named Close confirmation.
Server Box is AGPL study evidence only; no code, translation, assets or dependency was copied.

Input uses one labeled Text menu rather than a clipboard glyph representing unrelated
text actions. Ctrl and Alt keep fixed touch areas, selected semantics and a visible armed
indicator. Esc stays beside them; a labeled Keys disclosure opens the other keys in a
second row, with Tab and all four arrows together at the tested narrow width. It starts collapsed to preserve
terminal height, and its expansion survives Activity recreation. Selection/copy and multiline paste
confirmation retain their existing boundaries. Settings keep independent saved drafts,
one Back path and show a save panel only for edits, saving or failure; success is compact.

Revised phone acceptance remains pending. Host-rendered Compose screens and components can reveal
clipping and spacing but cannot verify the integrated workspace, Android IME, native cell grid,
TalkBack, physical touch, or session lifetime on the phone.


The broader flow review also keeps ready environments in the session sidebar when they
have no sessions, adds visible Restore for archives and aligns Archive with the other pinned destinations, and promotes Linux
settings into the same grouped-row pattern. The environment header wraps its actions on
narrow windows instead of squeezing the section label. Guest settings use concise help,
a saveable Advanced disclosure and the same reachable edit/saving/error/success area as
app settings. The settings index marks retained unsaved drafts. Guest path failures are attributed to their fields through the save completion callback;
local address/path checks also mark their input. Restore backup retains a text label.
Cancelled/interrupted setup uses a quiet interrupted state. Failed setup has a bounded
reason, a distinct attention surface, Retry and full Details. These are original changes.

Host inspection covers the sidebar in English/Arabic and large text, archives, environment
empty/ready/progress/interrupted states, settings subpages, guest settings, save feedback,
recovery content and menu content. Animated sheet/popup windows do not render reliably in
Layoutlib; their static native content is inspected directly. Placement and transitions
remain phone checks. The temporary renderer configuration is not part of the application.


### Settings completeness audit

Audited the revised component renders and affected source against
[Settings — Mobile app](https://www.checklist.design/mobile/settings).
Touch, focus and phone behavior still require actual captures and device testing.

| Status | Checklist item | Evidence / remaining limit |
| --- | --- | --- |
| Present | **Grouped table layout** — Settings organised into clearly labelled sections (Account, Notifications, Privacy, Support) using the native grouped list pattern | Terminal/input and help groups are visible in the revised settings render. |
| Present | **Native toggle controls** — Binary settings presented with the platform-native toggle switch (UISwitch on iOS, Material Switch on Android) | Guest compatibility uses Material Switch, visible in the supplied phone capture; behavior awaits revised phone testing. |
| Not needed here | **Destructive actions grouped** — Log out, delete account, and other irreversible actions in their own section at the bottom, visually distinguished in red | No destructive app settings; session/environment deletion lives in contextual menus after a divider, with confirmation. |
| Not needed here | **Account details at top** — The user's avatar, name, and email shown prominently at the top of settings, a clear anchor for whose account is being managed | This local terminal has no user account. |
| Present | **Deep link to specific settings** — A direct link from relevant in-app prompts to the specific settings screen they reference — notification preferences, privacy, and so on. | Command management requests Saved commands directly; settings retain that destination. Source evidence. |
| Present | **Support and feedback access** — A clear path to contact support, submit feedback, or access help documentation, accessible from within settings. | Keyboard & clipboard opens the in-app input help. |
| Present | **App version** — The current app version shown at the bottom of settings — essential for support conversations and identifying build-specific issues. | Version and source revision now appear below the settings groups; About retains full build identity. |
| Partially present | **Legal links** — Links to the Privacy Policy and Terms of Service accessible within settings, as required by app stores. | About exposes distributed licenses. Store-specific policy/terms requirements are outside this UI refinement and were not verified. |
