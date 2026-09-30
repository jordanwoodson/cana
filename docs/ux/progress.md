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
