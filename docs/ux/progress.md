# Cana UX overhaul: progress and evidence

Source: `/home/jordan/projects/cana/cana-ux-plan-prompt.md`. Base: `roadmap` at `047ed46`. Work branch: `ux` (local only).

## Decisions

- D1–D10: follow the defaults in the source prompt. In particular, retain one main screen, turn wallpaper colours off by default, use snackbars only for fully verified successes, keep risk colours semantic, use a one-time warranty onboarding, and allow conflict-checked undo from History.
- The source prompt describes lint as 31 errors and 208 warnings. The fresh `lintDebug` run on this exact checkout succeeded and emitted 493 warnings plus one hint, with no errors; 413 warnings are `MissingTranslation`. Track this measured baseline for the final comparison.

## Baseline (Task 1.1)

- 2026-09-30: clean `roadmap` checkout at `047ed46`; created `ux` from it. No code changes existed at the branch point.
- Reviewed all 18 screenshots in `ux-review/before/` before editing the app. Main list at 610×1356 has a tall header, repeated badges and large cards; Profile switcher names differ from labels elsewhere.
- `testDebugUnitTest`: 48 XML suites, 197 tests, 0 failures, 0 errors, 0 skipped; Gradle `BUILD SUCCESSFUL`. Log: `/tmp/cana-ux-baseline-unit.log`.
- `lintDebug`: Gradle `BUILD SUCCESSFUL`; `app/build/reports/lint-results-debug.xml` contains 0 errors, 493 warnings, 1 hint. Log: `/tmp/cana-ux-baseline-lint.log`.
- Emulator `emulator-5554` is booted (`sys.boot_completed=1`). Display override is 610×1356 at 240 dpi. Personal user 0 and Work user 10 are running. Shizuku server PID 2245 is active. Release-signed Cana `3.2.2-cana.7` is installed for both users. No command was sent to a real phone.

## Task and phase record

Each later entry records the change, commit, test counts, device checks and screenshot paths. Safety checks follow §3 of the source prompt.

### Task 1.2 — Shared profile directory and names

- `baa978c` adds a last-good Shizuku profile directory, refreshes it on startup and authorization, and routes main, comparison, history, dialogs, OTA and preset preview labels through translated profile names. Fallback for an unknown entry is Personal only for current user 0, otherwise “User N”. Raw IDs remain in the switcher subtitle and saved technical data. No package action targeting or authentication code changed.
- `ProfileDirectoryTest` and `ProfileNamingTest`: five new JVM cases. The red run failed on missing types; after implementation the focused run passed. A JVM-specific Android logger initialization failure was isolated by injecting the error reporter, then the focused run passed.
- Final `testDebugUnitTest assembleDebug assembleDebugAndroidTest`: **202/202 JVM tests pass**, both APKs build (`/tmp/cana-ux-phase1-profile-final.log`).
- Device classes on `emulator-5554`: `DebloatUiDeviceTest` **2/2 pass** (`/tmp/cana-ux-profile-debloat-device.log`), `PrivacyUiDeviceTest` **2/2 pass** on rerun (`/tmp/cana-ux-profile-privacy-device-rerun.log`), `UpdateCleanupUiTest` **2/2 pass** (`/tmp/cana-ux-profile-update-device.log`). The first Privacy run failed **2/2** because the preceding connected test had removed the target app, changing its UID and losing Shizuku authorization. Reinstalled and granted permission through Cana’s dialog before rerunning; no test assertions were weakened.
- Screenshot: `ux-review/after/phase1/01-main-list-light.png` at 610×1356, showing “Personal” in the main header. The profile switcher showed “Personal” and “Work profile”.
- `PresetImportUiDeviceTest` remains to run in the Phase 1 device gate after the shared components and copy changes. The Android test runner removes Cana after each class, so reinstall, AOT compile and authorize through the app before another Shizuku-dependent class.

### Task 1.3 — Semantic risk palette and wallpaper-colour preference

- `b885e5b` is the upstreamable badge/icon change: theme contrast pairs replace raw neon badge colours, and Unsafe uses `Dangerous` instead of the removal-like Close icon. It contains no Cana profile or preference code.
- `9b64762` adds fixed light/dark `RiskColors` in `CanaColors.kt`, supplied independently of wallpaper colours. Proto field 15, store and view model flows, an Android 12+ Settings switch, and a pre-first-frame DataStore read make wallpaper colours opt-in without a startup palette flash. `876be30` fixes a lint error in the previous profile fallback by observing `LocalResources` for locale changes.
- `CanaColorsTest` checks all eight risk color pairs at WCAG 4.5:1 and profile tint mapping; `RiskPresentationTest` checks the four requested risk icons. Each test was run red before implementation and green afterward. Final `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`: **205/205 JVM tests pass**, both APKs build, lint succeeds (`/tmp/cana-ux-colors-final-gate.log`).
- Lint after Task 1.3: **0 errors, 505 warnings, 1 hint**; `MissingTranslation` is 418, up from 413 at baseline due to five new English string keys. The first lint attempt found one new `LocalContextGetResourceValueCall` error; `876be30` corrected it, and the rerun has zero errors.
- Emulator screenshots at 610×1356: `ux-review/after/phase1/01-colors-light-personal-static.png`, `01-colors-light-personal-dynamic.png`, `01-colors-light-personal-dynamic-relaunch.png`, `18-colors-light-work-dynamic.png`, `18-colors-dark-work-dynamic.png`, `18-colors-dark-work-static.png`, and `16-settings-light-dynamic-on.png`. The dynamic setting was switched through Settings and retained after relaunch. Fixed risk chips kept their semantic colors when the surrounding UI changed from mint to wallpaper blue and between light and dark. The complete theme/profile matrix remains in Phase 9.

### Task 1.4 — Shared components

- `be6ca36` added `RiskChip`, `StatusChip`, `CanaBanner`, `InfoDisclosure`, `SectionHeader`, `EmptyState`, and generic `ChoiceRow` in `CanaComponents.kt`. Each has a live call site: badges, app-list notices, app-detail size explanation, Settings appearance header, app-list empty state, and preset import source choice. Shared `ScreenTopBar` now supports a subtitle and container color; every secondary page uses it. Light and dark previews cover the components and top bar.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` passed after implementation, then `testDebugUnitTest assembleDebug lintDebug` passed after adding previews. **205/205 JVM tests pass**, both APKs build, lint has **0 errors, 512 warnings, 1 hint**. Five of the added warnings are new English keys (`MissingTranslation`, 423 total), consistent with the English-only resource rule. Logs: `/tmp/cana-ux-components-gate.log`, `/tmp/cana-ux-components-final.log`.
- Emulator `emulator-5554` at 610×1356: app list chips and empty search result rendered, App detail disclosure began collapsed and expanded when tapped, Settings showed the Appearance section header, and preset import switched between Clipboard and Text. Screenshots: `ux-review/after/phase1/01-components-list-dark.png`, `02-components-empty-dark.png`, `12-components-choice-dark.png`, `15-components-detail-dark.png`, `16-components-settings-dark.png`. Banner variants remain for later phase/device gates.

### Task 1.5 — Unified feedback

- `378a383` adds `UiMessages` to `CanaServices`, a single `SnackbarHostState` provided above navigation, a collector in `CantaApp`, and the same host in every screen Scaffold. Preset, Logs, tracker scan and restore-export feedback now posts to this bus. A search under `ui/` found **zero `Toast` calls** after migration. The message type carries optional action, callback and dismiss affordance for later Undo work.
- `UiMessagesTest` was red on missing types and then green; it verifies a producer independent of the collector delivers a message and preserves its action. Final `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`: **206/206 JVM tests pass**, both APKs build, lint succeeds with **0 errors, 512 warnings, 1 hint** (`/tmp/cana-ux-messages-final.log`).
- On `emulator-5554`, invalid preset JSON posted the new snackbar (`ux-review/after/phase1/12-messages-import-error-dark.png`). The dialog dimmed that global host, so the same error is now also shown inline while the input remains editable (`12-messages-import-inline-dark.png`). The emulator briefly showed a cold-start ANR after reinstall; tapping Wait resumed normal operation and the inline error was verified afterward. `ee6652b` extracts the real app collector and adds `CanaSnackbarNavigationUiTest`: posting from outside the source screen after navigation displayed on the destination, **1/1 device test pass** (`/tmp/cana-ux-snackbar-navigation-device.log`). Its compile was red before collector extraction.

### Task 1.6 — Copy and Settings clarity

- `a2d1686` is an upstreamable correction of English `reinstall` from “ReInstall” to “Reinstall” in place. `9476fcb` introduces the new English `device_settings` key for the menu, page title and History action label while preserving the old `system_controls` key. It also flattens the Advanced settings expander: Unsafe removal and the list URL are directly visible under a section header, and UI code no longer references “Click to expand”. The unused key remains for translation compatibility. This pulls the Advanced-section part of Task 6.3 forward; Phase 6 will still reorganize the full Settings page.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`: **206/206 JVM tests pass**, both APKs build, lint succeeds with **0 errors, 515 warnings, 1 hint** (`/tmp/cana-ux-copy-gate.log`). `MissingTranslation` is 424, one more than Task 1.5 for `device_settings`. The first compile found a removed `clickable` import still needed by the Settings footer; it was restored before the passing rerun.
- `PresetImportUiDeviceTest` on `emulator-5554`: **2/2 pass**, 0 skipped (`/tmp/cana-ux-phase1-preset-device.log`). Screenshots at 610×1356: `ux-review/after/phase1/17-copy-menu-dark.png`, `14-copy-device-settings-dark.png`, `16-copy-settings-flat-dark.png`. The test reset Cana's data and Shizuku authorization; the app was reinstalled, AOT-compiled, and permission restored through Cana's Shizuku dialog. The profile switcher then listed Personal and Work profile.

### Phase 1 verification gate

- Final `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`: **206/206 JVM tests**, both APKs, **0 lint errors, 515 warnings, 1 hint**, 424 `MissingTranslation` warnings (`/tmp/cana-ux-phase1-final-gate.log`). Other UI device classes run during Phase 1: Debloat **2/2**, Privacy **2/2** on rerun after reinstall and authorization, Update cleanup **2/2**, Preset import **2/2**, snackbar navigation **1/1**. Each was restricted to `emulator-5554`; no real device was used.
- After the navigation test, reinstalled and AOT-compiled Cana, dismissed the reset warranty dialog, and authorized it through the Shizuku prompt. The switcher again listed Personal and Work profile. Phase 1 changed presentation and message delivery, not the package-action execution path; the action and Undo invariant exercise is scheduled with Phases 3–5 when the new Undo affordance exists.

### Task 2.1 — Profile-first collapsible header

- `b4deab6` moves the selected profile into a dropdown chip beside the wordmark, removes the old profile icon action and subtitle row, and keeps Shizuku gating. The header and tabs use `LocalCanaColors.profileTint(kind)`; Personal is green and Work is teal. `enterAlwaysScrollBehavior` collapses the title bar while the tabs and filter chips stay pinned.
- `testDebugUnitTest assembleDebug`: **206/206 JVM tests pass** (`/tmp/cana-ux-header-build.log`); the corrected constrained title slot also built (`/tmp/cana-ux-header-width-build.log`). On the 610×1356 emulator, first render let the chip extend over Filters and More; constraining the title row to 216 dp restored all three action icons. Screenshots: `ux-review/after/phase2/01-header-personal-dark.png`, `18-header-work-dark.png`, `18-header-work-collapsed-dark.png`. The collapsed state moves tabs from y≈228 to y≈132, freeing 96 px. The three-extra-rows target awaits the denser rows in Task 2.4.
