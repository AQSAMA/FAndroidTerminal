# Headless UI rendering: framework comparison

Research date: 2026-11-17. Documentation/source inspection only; no builds, rendering experiments, emulators, or device tests were performed for this note. Compose experiment results belong to the parent task.

## What “without running the app” means

Distinguish **no installed app/device**, **no visible window**, and **no code execution**. Host rendering still executes view/layout/drawing code. A PNG contains pixels; a JSX/DOM/JSON snapshot describes structure. A simulator running without its window is still a simulator. These distinctions follow directly from the rendering paths below, rather than from a common cross-framework definition of “headless.”

| Framework / path | Actual pixels without a phone emulator? | What executes / key boundary |
| --- | --- | --- |
| Android Compose screenshot tests | Yes | Host Preview rendering through the screenshot plugin; Gradle tasks do not require opening Android Studio. This tests composables, rather than the installed app's runtime. [Android docs](https://developer.android.com/studio/preview/compose-screenshot-testing) |
| React web + Playwright | Yes | A real headless browser loads/renders the page; `page.screenshot()` or locator screenshots return image data. “Headless” removes the browser window, not browser execution. [Screenshots](https://playwright.dev/docs/screenshots), [test execution](https://playwright.dev/docs/running-tests) |
| React Native + Jest/test renderer | No, for this path | Component snapshots are serialized text; Node-based component tests exclude the underlying Android/iOS code. [RN testing](https://reactnative.dev/docs/testing-overview#testing-rendered-output) |
| React Native + Detox | Native pixels, but requires a device or simulator/emulator | `device.takeScreenshot()` and element screenshots capture the running native UI. Hiding an emulator window does not remove that runtime. [Screenshot API](https://wix.github.io/Detox/docs/guide/taking-screenshots/), [headless device setting](https://wix.github.io/Detox/docs/config/devices/) |
| Flutter widget golden tests | Yes | `flutter test` runs widgets in a host engine subprocess; `matchesGoldenFile` compares rendered images and `--update-goldens` writes baselines. [Golden API](https://api.flutter.dev/flutter/flutter_test/matchesGoldenFile.html), [host runner source](https://github.com/flutter/flutter/blob/main/packages/flutter_tools/lib/src/test/flutter_tester_device.dart) |
| SwiftUI ImageRenderer | Yes, for suitable views on an Apple host/runtime | Produces bitmap data from a supplied SwiftUI view; macOS rendering can avoid an iOS simulator, but does not establish iOS-specific appearance. [ImageRenderer](https://developer.apple.com/documentation/swiftui/imagerenderer) |
| Rune | Yes, for documented native desktop tooling | Runs the compiled app's headless path with offscreen GPU rendering; PNG, structured tree and scripted input are separate facilities. [Rune docs](https://github.com/anomalyco/rune/blob/631e8a67cb3c40225efa3f6c4f57b80e7b18f190/crates/rune/README.md#agent-tooling-headless-no-window-same-renderer-fixed-virtual-clock) |

## React web and React Native

React's `renderToStaticMarkup` produces an HTML string, not an image. A browser screenshot is a separate rendering step. Playwright supports full-page and individual-element PNG capture, so the same approach applies to other browser-rendered frameworks; this last point is an inference from its page/element API, not a special React renderer. [React API](https://react.dev/reference/react-dom/server/renderToStaticMarkup), [Playwright API](https://playwright.dev/docs/screenshots)

React Native documents its component snapshots as JSX-like strings and warns that its Node component tests do not exercise native platform implementations. React also deprecates `react-test-renderer`; it should not be presented as a maintained native-pixel renderer. [RN component tests](https://reactnative.dev/docs/testing-overview#component-tests), [React deprecation](https://react.dev/warnings/react-test-renderer)

For actual native pixels, Detox captures a whole device or a native view subtree. RN's setup supports Android development on desktop hosts with Android tooling and requires a Mac for local iOS native builds; Detox's iOS simulator setup uses macOS tooling. Therefore a Linux Node screenshot test is not an iOS native rendering solution. This describes these documented paths, not a proof that every possible custom host renderer is impossible. [Detox screenshots](https://wix.github.io/Detox/docs/guide/taking-screenshots/), [RN environment](https://reactnative.dev/docs/set-up-your-environment), [Detox setup](https://wix.github.io/Detox/docs/introduction/environment-setup/)

## Flutter

Typical flow: `testWidgets` → `tester.pumpWidget` → `expectLater(finder, matchesGoldenFile('screen.png'))`; generate/update using `flutter test --update-goldens`. The default host runner launches `flutter_tester`, with noninteractive execution and software/deterministic Skia rendering unless the Impeller option is enabled. This is an executing Flutter engine, not a text-only mock and not an Android emulator. [Golden API](https://api.flutter.dev/flutter/flutter_test/matchesGoldenFile.html), [runner implementation](https://github.com/flutter/flutter/blob/main/packages/flutter_tools/lib/src/test/flutter_tester_device.dart)

Fonts need explicit handling: the documented default test font is Ahem, and goldens may vary with host OS or Flutter version. Flutter's own golden-testing guide supports local Linux/macOS/Windows execution. Thus a host golden is useful pixel evidence, but not proof of platform-view/plugin/device integration. [Font caveats](https://api.flutter.dev/flutter/flutter_test/matchesGoldenFile.html#including-fonts), [framework golden guide](https://github.com/flutter/flutter/blob/main/docs/contributing/testing/Writing-a-golden-file-test-for-package-flutter.md)

## SwiftUI

`#Preview` declares views for Xcode's canvas; it does not by itself define a portable headless PNG command. Xcode selects a connected device or Simulator runtime for relevant previews. [Preview macros](https://developer.apple.com/documentation/xcode/adding-previews-to-your-interface-files), [runtime selection](https://developer.apple.com/documentation/updates/xcode?changes=l_10_6&language=objc)

`ImageRenderer(content:)` can instead rasterize a view to `CGImage`, `NSImage`, or `UIImage` on supported Apple platforms, including macOS 13+. Inference: a macOS host harness can render compatible SwiftUI content without launching an iPhone app; iOS-specific UIKit content still needs an iOS runtime context. This is not a Linux renderer. Apple's indexed documentation also explicitly excludes Core Animation-composited content such as web/media views and most UIKit/AppKit views, which can become placeholders. [ImageRenderer and platform availability](https://developer.apple.com/documentation/swiftui/imagerenderer), [Apple limitation text](https://developer.apple.com/documentation/swiftui/imagerenderer?changes=_4)

Full app/window screenshots are another path: Apple's UI automation example launches `XCUIApplication` and captures its window, or captures `XCUIScreen`. That is running UI capture rather than standalone view rasterization. [XCUIScreenshot](https://developer.apple.com/documentation/xcuiautomation/xcuiscreenshot)

## Rune: verified identity, maturity and exact tooling

Inspected revision: `631e8a67cb3c40225efa3f6c4f57b80e7b18f190` on the repository's `dev` branch. This is Anomaly's Rust framework at `anomalyco/rune`. Its README explicitly says **pre-release**, recommends pinning a `dev` revision, and lists **macOS, Windows, Linux and web**. Android/iOS are not advertised supported targets there; do not infer mobile readiness from “native.” [Pinned README](https://github.com/anomalyco/rune/blob/631e8a67cb3c40225efa3f6c4f57b80e7b18f190/README.md)

Documented commands below are examples for an already-built sample executable, not commands run during this research:

```sh
target/release/settings --snap out.png --size 620x520 --scale 2 --at 0.4
target/release/settings --tree tree.json --at 0.4
target/release/settings --script examples/settings/scripts/toggle.json --frames 30 --out frames/
```

The CLI supports scenes, theme, virtual time, keyed or coordinate input, pointer/keyboard/resize scripts, frame sequences, and video. `--snap` creates pixels; `--tree` creates structural JSON; `--trace` creates timing/motion JSONL. `rune::tooling::headless_run()` exposes the run to application code. Debug builds include tooling; release builds require the `tooling` feature. Video needs ffmpeg. These are CLI/Rust APIs, not evidence of a standalone MCP service. [Pinned tooling documentation](https://github.com/anomalyco/rune/blob/631e8a67cb3c40225efa3f6c4f57b80e7b18f190/crates/rune/README.md#agent-tooling-headless-no-window-same-renderer-fixed-virtual-clock)

Source verification: `headless_configured` initializes the app/runtime, creates `Gpu` without a window surface, creates the regular `Renderer` and an `Offscreen` target, submits GPU work, reads back pixels and writes PNGs. Thus this is real offscreen rendering that still executes the app and needs a functioning graphics backend; it is not static Rust parsing or a DOM snapshot. Linux documentation says `--snap` needs neither a display nor the windowed XKB library. [Headless source](https://github.com/anomalyco/rune/blob/631e8a67cb3c40225efa3f6c4f57b80e7b18f190/crates/rune/src/tooling/headless.rs), [Linux requirements](https://github.com/anomalyco/rune/blob/631e8a67cb3c40225efa3f6c4f57b80e7b18f190/crates/rune/README.md#linux)

Inference for this project: Rune's integrated agent tooling is useful evidence that headless native rendering is feasible, but its documented platform list and pre-release status do not justify replacing this Android Compose application. Headless frames also do not measure physical display presentation; Rune itself distinguishes native presentation timing from virtual headless timing. [Rune README](https://github.com/anomalyco/rune/blob/631e8a67cb3c40225efa3f6c4f57b80e7b18f190/README.md), [timing documentation](https://github.com/anomalyco/rune/blob/631e8a67cb3c40225efa3f6c4f57b80e7b18f190/crates/rune/README.md#agent-tooling-headless-no-window-same-renderer-fixed-virtual-clock)

## Other useful paths and project implication

Android Views also have host screenshot options: Paparazzi explicitly renders screens without devices/emulators, and Roborazzi uses Robolectric Native Graphics for JVM screenshots, including Compose support. These are alternatives with different integration needs, rather than reasons to add multiple screenshot stacks now. [Paparazzi](https://github.com/cashapp/paparazzi), [Roborazzi](https://github.com/takahirom/roborazzi)

Qt Quick offers `QQuickItem::grabToImage`, explicitly rendering an offscreen surface and reading it into an image. This establishes offscreen pixel capture, not a claim that every Qt deployment works on a display-free host without setup. [Qt API](https://doc.qt.io/qt-6/qquickitem.html#grabToImage)

For FAndroidTerminal, host Compose renders can establish component layout, themes, RTL and populated UI states. They cannot establish actual root/PTY/Shizuku operation or Android device policy; those belong to separate runtime/device acceptance. This is a scope inference from host Preview rendering and this repository's runtime ownership boundaries, not a phone-verification claim. [Compose screenshot docs](https://developer.android.com/studio/preview/compose-screenshot-testing), [local architecture](architecture.md)


## Compose experiment in this workspace

The parent task ran the official alpha16 screenshot plugin with JDK17/AGP9.4.1,
one Gradle worker, two CPU cores and a 1.5 GiB Gradle heap (1 GiB screenshot heap).
All three previews rendered successfully: actual TerminalKeyControls, the Arabic
QuickControlsEditor and WorkingModeSettings. The last uses a sample Debian record,
in-memory preference fixtures and the ungranted-storage preview state; it does not
connect to Shizuku, request root, mount filesystems or install/run an APK. Generated
PNG files and the exact temporary renderer are under ignored `build/ui-previews/`.
The temporary plugin/source-set changes were restored after generation. A long-key
label wrapping defect found in the rendered image was fixed in production code.
No claim of phone-runtime verification follows from this experiment.
