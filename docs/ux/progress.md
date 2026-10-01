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

### Task 2.2 — Compact counted tabs

- `385680f` uses a text-only `PrimaryTabRow` and English plural keys. Counts come from the selected profile's complete `allApps` inventory, independent of active filters. The old tab string keys remain. `testDebugUnitTest assembleDebug`: **206/206 JVM tests pass** (`/tmp/cana-ux-tabs-build.log`).
- At 610×1356 with “Only system” active, Personal showed “Installed · 182” and “Removed · 0”; Work showed “Installed · 167” and “Removed · 15”. The tabs became about 36 px shorter. Screenshots: `ux-review/after/phase2/01-tabs-personal-dark.png`, `18-tabs-work-dark.png`.

### Task 2.3 — Counted selection header

- `60608e9` keeps `SelectionSummary` and its visible-only toggle, but presents a 48 dp row with “N apps” or “X of N selected”. The whole row carries one checkbox role and accessible count label; the child checkbox and text do not repeat it. English plural keys were added; the old `selection_visible` key stays.
- `testDebugUnitTest assembleDebug`: **206/206 JVM tests pass** (`/tmp/cana-ux-selection-header-build.log`); the small spacing follow-up compiled after restoring its `width` import (`/tmp/cana-ux-selection-header-final.log`). On `emulator-5554`, “Only system” yielded a single “179 apps” accessibility node, selecting one app changed it to “1 of 179 selected”, and tapping the row selected all 179 visible apps without an operation. Screenshot: `ux-review/after/phase2/01-selection-header-partial-dark.png` (before the final 8 dp label spacing).

### Task 2.4 — Compact flat app rows

- `40bd3e7` replaces elevated cards with 88 dp `ListItem` rows and inset dividers. Names and packages each use one ellipsized line; the second supporting line holds semantic chips and a short size. Selected rows use `secondaryContainer`; disabled content uses 0.38 alpha. The per-row System badge and repeated “APK files” label are gone. Leftover updated-system apps get a status chip and known update size. A long press selects the row with haptic feedback, while a tap still opens details. Other-profile icons use Android's user badge.
- `AppSizeUiTest` was updated first and passed **2/2** on `emulator-5554` (`/tmp/cana-ux-app-row-device.log`). `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passed (`/tmp/cana-ux-app-row-build.log`), and `lintDebug` passed (`/tmp/cana-ux-task24-lint.log`).
- The collapsed Personal list showed eight full rows at 610×1356 (`ux-review/after/phase2/01-compact-rows-collapsed-dark.png`). The Work list displayed briefcase badges on each icon (`18-compact-rows-work-dark.png`); long pressing one row selected it and changed the header to “1 of 165 selected” (`18-compact-rows-longpress-dark.png`). Shizuku permission was restored through its Application management UI after the device test reset the app.

### Task 2.5 — Banners, loading and empty states

- `4fc9493` adds a details slot to `CanaBanner` and moves tracker scan progress, recommendation refresh, OTA notices, and batch progress/recovery notices onto it. The leftover-updates and inventory-error notices already used `CanaBanner` after Task 1.4. Full-screen loading now appears only when the profile inventory is empty; a refresh with existing apps shows a linear indicator above the retained list. The empty Removed tab offers “Clear filters” only when removed apps exist but filters hide them.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` passed after these changes and the Task 2.6 label fix (`/tmp/cana-ux-task25-final.log`): **206/206 JVM tests**, both APKs, **0 lint errors, 521 warnings, 1 hint**; 429 warnings are `MissingTranslation` for English-only keys. On `emulator-5554`, the empty Personal Removed tab with “Only system” active showed no “Clear filters” action (`ux-review/after/phase2/11-empty-removed-dark.png`).

### Task 2.6 — Active filter chip labels

- `7110400` adds descriptive remove-filter labels to the “Only system” and “Saved collection” close icons. The scope-label change (“System apps” / “User apps”) depends on Task 4.1's `AppScope` and remains to be finished there. The Phase 2 build/lint above includes this commit.

### Phase 2 verification gate

- `AppSizeUiTest` **2/2**, `CategoryFilterDeviceTest` **1/1** (`/tmp/cana-ux-phase2-category-device.log`), and `SelectionUxDeviceTest` **5/5** (`/tmp/cana-ux-phase2-selection-device.log`) passed on `emulator-5554`. The latter two were run after the Phase 2 code commits; neither performs package mutations. Main-list and Work screenshots are recorded under Tasks 2.1–2.5. Task 4.1 will complete the scope-chip wording, and the full theme/profile matrix remains for Phase 9.

### Task 3.1 — State-aware action policy

- `450ccef` adds the pure `ActionChoices.forSelection` policy and makes `AppListViewModel.defaultAction` delegate to it. Installed choices depend on mixed enabled/suspended states, with Enable primary for an all-disabled selection and Disable primary when an Expert or Unsafe app is selected. Removed choices use Reinstall as primary and include Remove updates only for an updated system app. The primary is excluded from secondary groups.
- Five new `SelectionPolicyTest` cases cover mixed states, all disabled, Expert/Unsafe, both removed variants, and empty selection. The new tests failed to compile before the policy existed and then passed **8/8** with the existing selection tests (`/tmp/cana-ux-action-choices-red.log`, `/tmp/cana-ux-action-choices-green.log`). Full `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passed **211/211 JVM tests**, both APKs (`/tmp/cana-ux-action-choices-build.log`). No device state changes are involved in this pure policy task.

### Task 3.2 — Compact selection bar and grouped actions

- `8f0148a` replaces the tall three-row bar with a 64 dp row above the navigation inset: clear icon, selected count that opens the review list, a primary verb, and a separate menu button. Alternatives are grouped under Reversible and Removes app with action icons; removal choices use the error colour. The primary action comes from Task 3.1's policy. New short count/menu keys live only in English resources.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` passed **211/211 JVM tests**, both APKs, **0 lint errors, 528 warnings, 1 hint**; `MissingTranslation` is 434 (`/tmp/cana-ux-selection-bar-final-build.log`). `SelectionUxDeviceTest` passed **5/5** on `emulator-5554` (`/tmp/cana-ux-selection-bar-device.log`). Screenshots at 610×1356: `ux-review/after/phase3/05-selection-bar-personal-dark.png` and `06-selection-menu-personal-dark.png` show the primary Uninstall button and grouped menu without clipping.
- `DebloatUiDeviceTest` initially passed its updated unsafe-selection case but timed out in the unrelated component check. The instrumentation APK had a separate UID and no declared Shizuku access; the second check also exposed a shell-service binding to the test package. Those harness failures were resolved below. No package action was executed by the Debloat tests.

### Task 3.3 — Specific, risk-aware confirmation

- `2bf3e76` adds action/count/profile titles, concise consequences, RiskChip rows with explicit exclusion reasons, and a skipped count only when nonzero. Safety and shared-update warnings use error-toned `CanaBanner`s; the existing acknowledgement and confirm enablement checks remain. Destructive confirm verbs use the error colour. Expert/Unsafe uninstall reviews offer “Disable instead,” which opens a fresh Disable review for the same included packages and captured profile. The old generic strings remain for translation compatibility.
- `b9782ef` binds the Shizuku shell service to the installed Cana package rather than whichever context created `CanaServices` (upstreamable). `4197198` declares Shizuku access in the instrumentation APK, allowing its distinct UID to be managed through Shizuku's app UI. An attempted test APK provider declaration was removed after logcat showed that Android test packaging omitted the provider class; it is not in the committed manifest. The Shizuku service was restarted with the exact command shown in its own “View command” dialog, restoring four authorized app instances. No shell permission grant was used.
- Final `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passed **211/211 JVM tests**, both APKs (`/tmp/cana-ux-shell-target-build.log`); `lintDebug` passed with **0 errors, 546 warnings, 1 hint**, including 449 `MissingTranslation` warnings for English-only keys (`/tmp/cana-ux-confirmation-lint.log`). Direct instrumentation on `emulator-5554` with both APKs authorized through Shizuku: `SafetyUiDeviceTest` **1/1**, `DebloatUiDeviceTest` **2/2**, `UpdateCleanupUiTest` **2/2**. The safety test verified the keyboard warning and acknowledgement gate; update cleanup retained shared-profile consent and cleanup. Earlier failures before the test authorization and shell target fix are documented by `/tmp/cana-ux-confirmation-safety-device.log` and the Task 3.2 logs.
- Screenshots at 610×1356: `ux-review/after/phase3/07-confirmation-personal-dark.png`, `07-confirmation-work-expert-dark.png`, `07-confirmation-work-disable-instead-dark.png`, `07-confirmation-work-expert-light.png`. The Work Expert review was inspected in both themes, and “Disable instead” opened a new review. All manually opened dialogs were canceled without executing their action.

### Task 3.4 — Action results and Undo feedback

- Success now posts an action-specific plural snackbar through the app-wide `UiMessages` collector. Changed batches offer Undo, which opens the existing recovery confirmation; failures and unknown outcomes retain a dialog with failures first. The dialog is hosted at the app root so its Undo callback survives navigation. Remove updates includes freed size. The “Hide success dialog” control and its reads are removed; its stored proto field and old English key remain for compatibility. `BatchFeedbackPolicyTest` was red before implementation and green afterward (4 cases).
- On `emulator-5554`, uninstalling Android System WebView from Work showed “Uninstalled 1 app” with Undo (`ux-review/after/phase3/08-feedback-result.png`). Android reported it absent for user 10 while still present for user 0. After the snackbar expired, History's Undo restored installation for user 10; it correctly reported unrecoverable app data as a failed recovery. A successful Work disable of the dedicated Cana Test Fixture showed “Disabled 1 app” with Undo (`08-feedback-disable.png`), and History's Undo restored `enabled=0` after the operation set `enabled=3`. Both packages are installed for Work again.
- Forcing a failure by externally uninstalling the fixture from Work after Cana opened its confirmation produced “Some changes failed” with the “already uninstalled for this profile” reason (`08-feedback-failure.png`); the fixture was immediately restored to Work. This was done only on `emulator-5554`. A later attempt to tap the snackbar Undo after a second Disable did not trigger another operation because the confirmation was no longer open; snackbar callback behavior remains covered by `UiMessagesTest` and the app-wide collector test, while the recovery path was exercised through History.
- Final `testDebugUnitTest lintDebug` passed **215/215 JVM tests**, **0 lint errors, 563 warnings, 1 hint**. The first lint pass found two new Compose resource-context errors; both were fixed by resolving the strings in composition before the coroutine. APKs built for the device validation (`/tmp/cana-ux-feedback-build.log`).

### Phase 3 verification gate

- The required Work Expert uninstall kept Personal WebView installed, and Undo restored Work installation. A Work fixture Disable and Undo restored its enabled state. The forced absent-package failure produced the result dialog; the fixture was restored. The relevant Phase 3 safety, debloat and cleanup instrumentation classes passed under Task 3.3. Task 3.4 raised the JVM suite to 215 and passed lint with zero errors.
- The profile chooser still showed raw “User ID” subtitles despite Task 1.2's no-ID intent. A small follow-up removes those subtitles while retaining the old string resource for translation compatibility; profile names and restriction warnings remain visible.

### Task 4.1 — Scope and filter sheet

- `AppScope` maps All/System/User to the existing `onlySystem` field and `Filter.user` ID, leaving saved-view IDs unchanged. `Filter.user` now belongs to `FilterGroup.SCOPE`, so User and Disabled can coexist. Applying a legacy saved view with contradictory system/user fields resolves to User. `AppScopeTest` was red before implementation and then passed its two mapping/coexistence cases.
- The sheet now shows saved views first in a horizontal row, with Save current view and deletion confirmation; Scope uses the shared segmented `ChoiceRow`. Sort labels are short. State, risk, category, and compact privacy controls follow, ending with a risk-explanation link. A sticky footer shows Clear and a live Show N apps count for the selected tab, replacing Close. Active scope chips say “System apps” or “User apps.”
- At 610×1356 in light theme, the sheet showed the initial System scope with “Show 179 apps” (`ux-review/after/phase4/02-filters-light.png`). Selecting User and Disabled together updated the footer to “Show 0 apps” (`02-filters-user-disabled-light.png`). `CategoryFilterDeviceTest` passed **2/2** on `emulator-5554`, including the combined state; the affected Work sort test passed **1/1**. After APK reinstall, a cold-start ANR was dismissed with Wait before inspecting the sheet.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` passed: **217/217 JVM tests**, both APKs, **0 lint errors, 581 warnings, 1 hint**. Existing translations were untouched; new copy is in the base English resources only.

### Phase 4 verification gate

- The filter count and active chips respond together to scope and state changes, while saved-view fields remain compatible. The device tests above cover the category and Work usage-sort behavior. Full theme/profile/accessibility matrix remains scheduled for Phase 9.

### Task 5.1 — App overview hierarchy

- The overview now starts with a 56 dp profile-badged icon, title and package name with a copy icon. A compact row shows risk (opening the badge explanation), live state, system/user status, version and APK size (opening the size explanation). `ActionChoices` drives the primary action; Uninstall is an error-coloured outline when secondary, and remaining verbs sit in the overflow menu. They still route through the existing confirmation flow.
- “About this app” leads with the UAD description, then source, suggestions and dependents when present; usage follows. The tracker summary keeps names and rescan/report actions visible while method, limitations, source and attribution move into `InfoDisclosure`. A tappable Android settings `ListItem` closes the overview.
- On `emulator-5554` at 610×1356 in light theme, Ad Privacy's primary Uninstall button appeared without scrolling at y≈529, and the description began at y≈711 (`ux-review/after/phase5/08-overview-ad-privacy-light.png`). Tapping its risk chip opened the explanation; tapping the size info icon opened the size explanation. `DebloatUiDeviceTest` has no detail-page locator to update. The APK install required AOT compilation before this cold inventory load; no device state was changed.
- `testDebugUnitTest assembleDebug lintDebug` passed **217/217 JVM tests**, APK build, **0 lint errors, 595 warnings, 1 hint**.

### Task 5.2 — Privacy tab controls

- `PrivacyUiPolicy` preselects only permissions that are both granted and mutable, and maps the live background/metered/network values to switch states. The policy's two JVM tests failed to compile before it existed and passed afterward. The revoke control now reads “Revoke N” and is disabled at zero. The embedded tab omits repeated package/profile lines and puts refresh in its section header.
- Background, Metered and Network now render as `ListItem`s with live-state switches, status chips for saved rules and the existing Forget action. Turning on opens the safety review; turning off is disabled without a revertable history record and explains “Restricted outside Cana.” A revertable off action also opens review, with acknowledgement when `undoNeedsConsent` says restoration may broaden access. The switch stays at its live value until a confirmed operation completes. Existing permission test tags remain, and new switch tags support the review test.
- On `emulator-5554`, the Work fixture showed three denied permissions unchecked, “Revoke 0” disabled, and live switches (`ux-review/after/phase5/10-privacy-work-loaded-light.png`). Tapping Background showed the review while the switch remained off (`10-privacy-work-review-light.png`); it was canceled without a change. `PrivacyUiDeviceTest` passed its new permission and pre-confirmation checks **2/2** on the initial class run. The existing preset roundtrip test initially failed on an ambiguous “Work profile” text locator; a more specific first-match locator passed on targeted rerun, giving **3/3 affected tests** on the final code.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` passed **219/219 JVM tests**, both APKs, **0 lint errors, 604 warnings, 1 hint**. The final test-APK rebuild after the locator fix also passed.

### Task 5.3 — Components tab

- Component entries now use `ListItem`s with the full class name, a kind `StatusChip`, tracker names in tertiary colour and a trailing switch. Embedded mode omits the package/profile already shown by the detail header. Tracker attribution and its Exodus/ODbL links are behind `InfoDisclosure`; tracker-list source management stays visible.
- A component operation posts a result through the app-wide snackbar channel; a changed record supplies Undo, which opens `UndoBatchDialog`. Recovery success also posts a snackbar; failures remain readable inline. The existing safety assessment, acknowledgement and confirmation still gate the operation.
- On `emulator-5554`, Ad Privacy showed the new rows and disabled switches with the root-backed Shizuku restriction (`ux-review/after/phase5/11-components-personal-light.png`). The affected `DebloatUiDeviceTest` component case passed **1/1**. This emulator's Shizuku is not root-backed, so a component mutation and its snackbar Undo could not be exercised on-device; the route compiled and uses the already-tested app message channel.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` passed **219/219 JVM tests**, both APKs, **0 lint errors, 609 warnings, 1 hint**.

### Phase 5 verification gate

- Overview actions were visible without scrolling on the 610×1356 viewport. The Work Privacy tab showed denied permissions unchecked, a disabled Revoke 0, and review before restriction; device tests cover these behaviors. Components retained its safety lock on this non-root emulator. No Privacy or component state was changed during visual review.

### Task 6.1 — Grouped overflow navigation

- The main overflow now uses flexible minimum width, leading icons, and four divider-separated groups: Presets/History; Privacy dashboard/Compare profiles/Device settings; tracker scan; Settings. Badge info lives in Filters and the detail risk chip; Logs will move to Settings → Diagnostics in Task 6.3. Existing destination routes and scanner action are unchanged. `compileDebugKotlin` passed; the final Phase 6 gate will capture the menu and run the full checks.

### Task 6.2 — Secondary top bars

- History, Privacy dashboard, Device settings, App detail, Settings, Logs, Presets and Compare already used `ScreenTopBar`; the remaining action mismatches were corrected. History now exports through a SaveAlt icon in the bar, Privacy dashboard refreshes through a bar icon, and Logs copies all through a bar icon instead of a FAB. Compare already had its Refresh icon. These actions have content descriptions and retain their existing behavior. `compileDebugKotlin` passed; Phase 6 gate will inspect the bars on-device.

### Task 6.3 — Settings structure

- A standalone upstreamable `SettingsItem` change adds an `enabled` parameter, prevents disabled row/switch interaction and dims its content. `compileDebugKotlin` passed. The dependent Bloat list control will use this in the Settings restructuring commit.
- Settings is now ordered Safety, Bloat list, Appearance, Permissions, Diagnostics and About. Unsafe warning copy and behavior remain. “Only on unmetered” uses `enabled = autoUpdateBloatList`; the wallpaper-colour setting remains Android 12+ only. Permission grants are two `ListItem`s with Allowed/Missing `StatusChip`s, one conditional Grant through Shizuku button, and supporting explanation. Diagnostics contains Logs navigation and the existing recovery-script exporter. The former footer is the About section. New strings are base English only; old unused keys remain.
- On `emulator-5554` at 610×1356 light theme, the grouped overflow showed its intended order and dividers (`ux-review/after/phase6/03-menu-light.png`). Settings' upper and lower sections fit without clipping (`16-settings-light.png`, `16-settings-lower-light.png`). Turning auto-update off visibly dimmed and disabled the unmetered row (`16-settings-unmetered-disabled-light.png`); tapping that row had no effect, and auto-update was restored to its original on state. Logs opened from Diagnostics with Copy all in its top bar (`16-logs-topbar-light.png`). A cold-start ANR occurred during inventory load after install; force-stopping and relaunching the emulator app cleared it before these screenshots.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` passed **219/219 JVM tests**, both APKs, **0 lint errors, 623 warnings, 1 hint**. No permission-grant action was tapped and no `pm grant` command was used.

### Phase 6 verification gate

- The overflow, Settings sections, dependent toggle, Diagnostics route and Logs top bar were inspected on `emulator-5554`. Existing `ScreenTopBar` users were confirmed in code; History and Privacy dashboard actions were moved to icons and built successfully. The full light/dark, profile and accessibility pass remains in Phase 9.

### Task 7.1 — Device settings

- The page now leads with a short device-wide warning and an information disclosure. Each control card has a description and a selected segmented choice: Default/On/Off for the three global scanning/data controls, Off/On for Data Saver, Default/GrapheneOS/Off for portal checks, and Default/Automatic/Off/Custom for Private DNS. Hostname entry, suggestions and Apply appear only for Custom; portal Off still requires confirmation. Revert buttons and existing test tags remain. Successful operations use a snackbar, while invalid input and failed operations stay visible as errors.
- `SystemUiDeviceTest` passed **1/1** on `emulator-5554`. Its UI assertions cover the DNS custom flow, invalid hostname, portal confirmation, changed Wi-Fi value, Revert affordance, Data Saver state, and a direct undo of the exact recorded Wi-Fi operation with state restoration. Shizuku's manager repeatedly stole focus after shell startup, so injected taps on the Revert control were unreliable; a manual app-side tap is scheduled for the Phase 7 visual gate. The test's direct undo verified the backend and returned Wi-Fi to its starting value.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` passed **225/225 JVM tests**, both APKs, **0 lint errors, 699 warnings, 1 hint** across the then-current Phase 7/8 working tree. Device visual review is pending the Phase 7 gate.

### Task 7.2 — Compare profiles

- `ProfileComparison.rows` now filters to differing available-cell status, disabled, suspended and saved-privacy tuples. Unavailable cells are ignored, and a single available cell is retained. Three new JVM tests cover these cases; the full suite passes 225/225.
- The comparison defaults to Only differences with two or more selected profiles, uses 150 dp app and 108 dp profile columns, omits empty privacy copy, and opens App detail from installed or removed cells. The two explanatory paragraphs are condensed into an intro and disclosure. Profile chips continue using `displayName()`.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passed. Device visual review is pending the Phase 7 verification gate.

### Task 7.3 — History cards and undo policy

- `ManagementPolicy.undoableBatchIds` derives candidates from the same rules as `lastBatch`, blocks running or incomplete batches, and excludes older batches whose app/profile or device-control keys overlap a later candidate. Successful undo removes the later candidate, exposing an older batch again. Three JVM tests cover the latest/disjoint/overlap, resolved overlap, and incomplete/running cases; the full suite passes 225/225.
- History uses per-batch Undo buttons, action/count/profile titles, status chips, nonzero counts, Details/Hide details, and app names when an inventory has them. The top Undo last batch button is gone. Empty and older-record copy is plain language. `ManagementUiDeviceTest` now targets its batch card; device execution is pending the Phase 7 gate.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passed.
- The focused `ManagementUiDeviceTest#historyUndoRestoresWorkAppAndReportsResult` passed **1/1** after its direct `HistoryPage` composition supplied the same snackbar host as `CantaApp`. It disabled the fixture in Work, used the new card Undo, then verified Work restored and Personal stayed enabled. A failed initial test run exposed the missing test provider; the Work fixture was reset before the passing rerun.

### Task 7.4 — Privacy dashboard

- Saved restriction cards resolve app names from each profile inventory, falling back to the package, and show the profile as a subtitle. Restriction type and verification state use status chips; live enforcement has its own line. The existing Review and Forget actions remain. The empty view uses `EmptyState`, and refresh remains in the top bar.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passed 225/225 JVM tests. Device visual review is pending the Phase 7 gate.

### Task 7.5 — Presets

- The empty state now uses Inventory2. Creating a preset states the exact removed-app count and selected profile, with an expandable app-name/package list; the saved app set matches that preview. Preset cards expose their stored profile kind as a chip.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passed 225/225 JVM tests. Device visual review is pending the Phase 7 gate.

### Task 7.6 — Logs

- Logs remains under Settings → Diagnostics with Copy all in the top bar. Entry backgrounds now come from Material colour scheme containers for Debug, Info, Warning and Error. A blank log shows an empty state. Long-press copy and snackbar feedback are retained.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passed 225/225 JVM tests. Device visual review is pending the Phase 7 gate.

### Phase 7 verification gate

- `0e2ae35` follows up the secondary screens: Compare uses actual status icons and 48 dp cells, drops its scroll hint when both columns fit, and keeps the prior description key while adding a short intro key. History omits zero recovery counts and labels device settings as device-wide settings. The preset dialog hides its included-app expander when the capture is empty. The full working-tree gate passed **225/225 JVM tests**, both APKs and **0 lint errors, 705 warnings, 1 hint** before the final string-key adjustment; the final gate will remeasure lint.
- At 610×1356 in light theme, Compare showed 16 differing apps and both profile columns without horizontal scrolling (`ux-review/after/phase7/14-compare-light.png`). Tapping a Work Removed cell opened that app's Work detail. History showed nonzero counts, card-level Undo and the device-wide title (`12-history-light.png`). A Work preset preview reported 15 removed apps and expanded their names (`17-presets-create-work-light.png`); Personal with zero removed apps showed no meaningless expander. The empty Privacy dashboard and Presets views were captured at `13-privacy-dashboard-empty-light.png` and `17-presets-empty-light.png`; a populated Privacy dashboard card was not exercised during this visual pass. Logs entries and scheme colours were captured at `16-logs-light.png`.
- A manual Wi-Fi scanning change to Off and tap on Revert in Device settings returned it to On, removed the Revert control and restored Android's `wifi_scan_always_enabled` value to `1` (`15-device-settings-light.png`, `15-device-settings-custom-light.png`). `SystemUiDeviceTest` passed **1/1** and `ManagementUiDeviceTest#historyUndoRestoresWorkAppAndReportsResult` passed **1/1**. The latter disabled the fixture in Work, undid it from its History card, and confirmed Personal stayed enabled. The earlier Phase 3 Expert Work uninstall, acknowledgement, profile isolation and undo checks still apply; Phase 7 changed the History affordance rather than the action path.

### Phase 8 — First-run onboarding

- `Screen.Welcome` is the start route until `disable_risk_dialog` has been read; the NavHost waits for that read. Step 1 explains Cana and requires the risk acknowledgement. Step 2 embeds the existing Shizuku checklist. Authorized users with multiple profiles see a final radio choice with profile names and icons. Finish saves the acknowledgement before navigating to Main and clearing Welcome from the back stack. `NoWarrantyDialog` remains in the source but is no longer shown by `MainContent`.
- After emulator-only `pm clear`, Continue could not advance before acknowledgement. The three screens rendered at 610×1356 (`ux-review/after/phase8/19-welcome-step1-light.png`, `19-welcome-step2-light.png`, `19-welcome-step3-light.png`). Selecting Work opened Main with Work selected. A force-stop/relaunch opened Main directly without a Welcome flash; the existing main screen's profile selection returned to Personal, so the onboarding selection applies to the initial session. The cold emulator occasionally took tens of seconds to render inventory; this preceded Phase 8.
- `ShizukuAuthorizationUiTest` passed **3/3** on `emulator-5554`. `ShizukuLifecycleUiTest` reported **OK (1 test)**, but its unauthorized-app assumption was not met on this already-authorized emulator, so its recreation assertions were skipped. The updated locator compiles. `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` passed: **225/225 JVM tests**, both APKs, **0 lint errors, 707 warnings, 1 hint**, including 565 `MissingTranslation` warnings (`/tmp/cana-ux-phase8-gate.log`).
