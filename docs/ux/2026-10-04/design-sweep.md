# October 4 design and branding sweep

Cana keeps its Dracula palette, bundled JetBrains Mono, terminal wordmark, and Open C launcher mark. This pass brings those assets into a clearer Android interface: distinct dark surfaces, rounded groups, stronger headings, readable secondary text, and layouts that make room for enlarged text. Inventory remains a dense list so app names, risk, state, and selection stay easy to compare.

The direction follows the current [Material 3 design-system guidance](https://developer.android.com/develop/ui/compose/designsystems/material3) and Google's [Material expressive research](https://design.google/library/expressive-material-design-google-research): use shape, hierarchy, and contrast to clarify purpose. Cana's existing workflows and safety reviews remain the basis of the interaction design.

## Changes and review

| Area | Change and purpose |
| --- | --- |
| Shared theme | Separate low, regular, and raised containers; use an 8–28dp shape scale and stronger JetBrains Mono title weights. Secondary text is distinct from primary text while retaining contrast. Control outlines and inline errors now remain readable on raised surfaces. Risk colors retain their established meaning. |
| Banners | Keep the icon/message in a full-width row and move actions into a wrapping row beneath it. This fixes the baseline banner whose message disappears beside long actions at doubled text size. Batch and OTA actions use wrapping layouts too. |
| Inventory and selection | Give app names a stronger weight, simplify the profile chip, and raise the rounded selection footer. Unavailable menu actions use a visibly disabled fill. Existing hidden-selection and profile targeting logic is preserved. |
| Secondary screens | Let headings set their bar height, add heading semantics, and retain standard system insets and the mirrored Back control. App details use a grouped identity card; the complete app name remains in its scrollable content. |
| Settings | Group Safety, Recommendations, Permissions, Diagnostics, and About into rounded surfaces. Put descriptions below the title/control row. Decorative icons yield their space at enlarged text sizes. Each switch retains one named toggle target. Navigation rows show an arrow, and the version label no longer suggests an inactive action. |
| Branding and welcome | Reuse the actual Open C mark with the terminal wordmark and tagline in About and welcome. Welcome gains a three-step progress indicator; its brand content scrolls with the consent body. Acknowledgment, Back, and continuation behavior are retained. |
| Empty presets | Allow the empty state to scroll at large text sizes, use a tonal icon tile, and remove duplicate spoken labels from decorative button icons. |
| Preset import with keyboard | Give Compose ownership of dialog keyboard/system insets. The dialog keeps its title, Review, and Cancel on screen at native doubled text size instead of panning the title above the display. |
| Tracker scan | Place the scanned-app count above its action so it stays readable on a narrow screen with enlarged text. Scan eligibility, callback, profile targeting, and cancellation are retained. |
| Copy | Describe recommendation updates accurately without promising that an updated list makes removal safe. |

Static review found no introduced callback or targeting regression. It checked banner call-site compatibility, the single settings toggle, title wrapping/insets, app identity/actions, onboarding state, palette contrast, and the narrow RTL header. Follow-up reviews checked actual rendered-line assertions, capture API guards, dialog inset ownership on API 28 and newer, and named heading semantics. The title wrapper merges its text with the heading marker while retaining separate Back/actions/subtitle nodes; this follows the [Compose accessibility implementation notes](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/accessibility/android_a11y_implementation_notes.md).

## Verification

The local gate passed `:app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest`: **248 unit tests in 60 suites, zero failures/errors/skips; release lint has zero errors, 741 warnings, and one hint**. This is a passing gate, not warning-free lint or complete localization.

The [normal-text suite](verification/normal-final.log) reported **28 tests passed** in 556.201 seconds: 24 focused control checks, three populated preset/history/privacy fixtures, and the general fixture walkthrough. The enclosing adb session ended with exit 143 after complete JUnit results; that transport result is retained rather than presented as a successful shell exit. The dedicated component regressions check drawn text, callback behavior, RTL Back placement, and tracker status/action ordering.

The [final enlarged-text fixture walkthrough](verification/fixtures-font2.0-final.log) passed in 414.983 seconds with `captureComparison=false`. It covered inventory, search and keyboard dismissal, filters including both tracker controls, selection/review/hidden selection, app details, settings/log copy, device settings, empty presets/history/privacy, import with the keyboard, import review, restore review, and onboarding/back navigation.

Native checks use an isolated Android 15 emulator at 360×800dp. Dedicated component checks use a 320dp content width and a Compose font scale of 2.0, including RTL navigation. General and populated fixtures execute no package, privacy, system-setting, or recovery action. They restore temporary app preferences, inventory, clipboard, and app-owned store snapshots. Disconnected Shizuku feedback is genuine; populated operation records are fixtures.

The software emulator required restoring the temporary JDK/SDK and increasing the guest `ro.hw_timeout_multiplier` to 10 before restarting its framework. Android documents this multiplier in [Build.java](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r4/core/java/android/os/Build.java) for slow software emulators. Optional background Google apps were disabled only on this disposable emulator. No production startup setting changed; settled-state and position assertions remain in place.

The baseline banner regression reproduced an invisible message. The baseline full walkthrough later timed out waiting for Compose to become idle at Filters, while its populated preset, history, and privacy tests passed. That attempt is retained as a failure. A `startAt=details` resume option preserves access to the remaining screens without claiming that a resumed run covered earlier inventory/filter states. The enlarged-text inventory/selection/details/settings walkthrough subsequently reached profile comparison, where its real inventory refresh exceeded the 90-second settling limit. That [attempt](verification/full-font2.0-comparison-attempt.log) is retained as a failure. Final fixture walkthroughs explicitly use `captureComparison=false`; comparison loading/refresh completion is excluded from those results. The default walkthrough still includes comparison. The privacy capture scrolls its scan action into view, then asserts both action and status are visible; the earlier status-only scroll [attempt](verification/filters-privacy-scroll-attempt.log) did not reveal the lower action. Filters are measured against the actual dialog window, retaining stable title/footer checks.

The tracker layout regression [fails against the preceding layout](verification/tracker-status-red.log) and passes after separating the status and action. Its test model is explicitly cleaned up afterward.

The rendered-line check distinguishes actual clipping from unused paragraph width: a short action measured 147px wide with its last rendered line ending at 145.6px, while Compose retained a 280px paragraph and reported width overflow. The assertion checks line bounds, height overflow, and ellipsis rather than treating that empty paragraph space as clipped text.

The import-title regression [failed before the inset fix](verification/import-keyboard-red.log) and [passed afterward](verification/import-keyboard-green.log). At native font scale 2.0 the old floating window moved to y=-51, placing the title's top above the display. `DialogProperties(decorFitsSystemWindows = false)` with safe-drawing padding handles the keyboard in Compose and preserves dialog callbacks. The [DialogProperties API](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogProperties) documents the older/newer Android input-mode behavior. The rejected [pre-fix capture](screenshots/attempts/import-keyboard-font2.0.png) is kept as defect evidence, not final UI evidence.

Capture helpers force a rendered-root copy before taking full-display screenshots and additionally save the direct app root as `*-window.png` when there is only one root. These images show the app at the native width/font size and omit system overlays. Dialogs/popups use full-display images so all windows are included, and full-display keyboard captures include the IME. Neighboring text files retain native configuration and semantics. A copied window buffer does not guarantee display composition has finished; visual inspection remains necessary. Earlier full-display selection and lower device-settings captures showed stale frames and were rejected as final evidence. The dedicated banner/RTL/tracker images also capture the actual Compose root.

The bulk normal/enlarged captures and suites precede the final semantics-only heading correction; their visual layout is unchanged. The new named-heading regression [fails on the preceding title node](verification/heading-red.log); the [final targeted suite](verification/heading-and-controls-final.log) passed **5 tests** in 45.426 seconds with shell exit 0, verifying the merged named title, wrapping, RTL Back behavior, banner/tracker layouts, and single settings/acknowledgment toggles. The required Gradle gate was rerun after this correction and passed.

This pass does not claim a formal TalkBack audit, complete translations, physical/OEM-device coverage, or renewed validation of every privileged operation. The previous [October 3 sweep](../2026-10-03/release-sweep.md) records connected operation checks; those are historical evidence, not results from this build.

## Captures

| Area | Native enlarged-text evidence |
| --- | --- |
| Banner actions | [320dp message and wrapping actions](screenshots/after/ui-components/banner-320dp-font2.0.png) |
| RTL header | [320dp wrapped heading and leading Back control](screenshots/after/ui-components/header-rtl-320dp-font2.0.png) |
| Tracker controls | [320dp status/action](screenshots/after/ui-components/tracker-status-320dp-font2.0.png), [360dp privacy filters](screenshots/after/ui-sweep/360dp-font2.0/04b-filters-privacy.png) |
| Selection | [Complete footer](screenshots/after/ui-sweep/360dp-font2.0/05-selection-window.png) |
| Identity | [App overview](screenshots/after/ui-sweep/360dp-font2.0/09-app-overview-window.png) |
| Branding | [About](screenshots/after/ui-sweep/360dp-font2.0/13-settings-about-window.png), [welcome](screenshots/after/ui-sweep/360dp-font2.0/24-welcome-safety-window.png) |
| System controls | [Scrolled device settings](screenshots/after/ui-sweep/360dp-font2.0/15-device-settings-bottom-window.png) |
| Keyboard | [Import title and actions above the IME](screenshots/after/ui-sweep/360dp-font2.0/20c-preset-import-keyboard.png) |

Final normal-text captures are archived under `screenshots/after/ui-sweep/360dp-font1.0`. Local debug captures show the existing debug version label; release CI assigns its version.

The native font-1 baseline captures are archived under `screenshots/before`. The baseline general walkthrough covered only the main/search prefix before its failure; its populated preset/history/privacy captures are separate fixtures.
