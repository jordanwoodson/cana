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
