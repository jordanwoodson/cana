# UX overhaul handoff

Implemented on local branch `ux` from `roadmap` at `047ed46`. The implementation and phase-by-phase checks are recorded in [progress.md](progress.md). No push, tag, release, version or signing change was made.

| ID | Status | Result |
| --- | --- | --- |
| U1 | Done | State-aware primary/secondary actions and destructive styling. |
| U2 | Done | Specific confirmation, risk and reversibility, and Disable instead. |
| U3 | Done | Dense rows, one-line package and no System row badge. |
| U4 | Done | Semantic risk colours and clearer Unsafe icon. |
| U5 | Done | App detail prioritizes description and actions; copy and disclosure remain. |
| U6 | Done | Shared profile names loaded at startup and used across screens. |
| U7 | Done | Profile title chip, tint and badged icons. |
| U8 | Done | Grouped overflow; Logs under Settings and badge help in context. |
| U9 | Done | Collapsing title bar. |
| U10 | Done | Counted text tabs. |
| U11 | Done | Single-row selection bar. |
| U12 | Done | Scope control, short sorts, live count and saved views first. |
| U13 | Done | List remains visible while reloading. |
| U14 | Done | Success snackbar with Undo, including verified Work restoration; uncertain/failure results stay prominent. |
| U15 | Done | Device controls show selected state and conditional DNS input. |
| U16 | Done | Differences-only comparison, compact cells and detail navigation. |
| U17 | Done | Clear History cards and conflict-checked per-batch Undo. |
| U18 | Done | Granted-only permission selection and live privacy switches. |
| U19 | Done | Grouped Settings and dependent controls. |
| U20 | Done | Consistent secondary top bars. |
| U21 | Done | Preset empty icon, capture preview and profile chip. |
| U22 | Done | Copy fixes. |
| U23 | Done | Optional wallpaper colours, off by default. |
| U24 | Done | One-time risk, Shizuku and profile onboarding. |
| U25 | Done | Shared banners. |
| U26 | Done | Populated dashboard cards with names, chips and live state. |
| U27 | Done differently | Full theme/profile matrix, 1.3 font scale and Arabic RTL inventory checked. The emulator ignored forced RTL with English alone, so an Arabic per-app locale was used for the populated RTL check. |
| U28 | Done | Count and Select all in list header. |
| U29 | Done | Long press toggles selection. |
| U30 | Done | Contextual empty states. |
| U31 | Done | Tidy Components tab and snackbar result route. Root-backed mutation/Undo could not be exercised on this non-root emulator. |
| U32 | Done | Theme-aware Logs with top-bar Copy all. |

## Commits

The complete ordered commit list is `git log --reverse --oneline roadmap..ux`. These three commits are independently upstreamable to Canta: `b885e5b` (contrast pairs and risk icon), `a2d1686` (Reinstall label casing), and `cf13bc1` (`SettingsItem.enabled`). Other commits implement Cana's UX or its device test and documentation coverage. Key phase endpoints are `a906c99` (Phase 1), `8b14092` (Phase 2), `4be20e7` (Phase 3), `a73b60a` (Phase 4), `a3d2037` (Phase 5), `a5dceba` (Phase 6), `309ea23` (Phase 7), `b5b70af` (Phase 8), `17bf496` (Phase 9), and `ea6e444`/`521329d` (completion audit tests). The ledger and this handoff are committed separately.

## Verification

The final offline gate passed: `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`. JVM tests rose from **197/197** at baseline to **225/225** (56 suites, no failure/error/skip). Lint was **0 errors, 493 warnings, 1 hint** before and **0 errors, 708 warnings, 1 hint** after; `MissingTranslation` rose from 413 to 565 because new strings were added only to base English resources.

Affected device tests passed on `emulator-5554`: `SelectionUxDeviceTest` 5/5, `DebloatUiDeviceTest` 2/2, `SafetyUiDeviceTest` 1/1, `UpdateCleanupUiTest` 2/2, `PrivacyUiDeviceTest` 3/3 affected cases, `PresetImportUiDeviceTest` 2/2, `CategoryFilterDeviceTest` 2/2, `AppSizeUiTest` 2/2, `CanaSnackbarNavigationUiTest` 1/1, `SystemUiDeviceTest` 1/1, `ManagementUiDeviceTest` History Undo 1/1 and snackbar Undo 1/1, `PrivacyDashboardUiDeviceTest` 1/1, `ShizukuAuthorizationUiTest` 3/3, and `ShizukuLifecycleUiTest` unauthorized path 1/1. Initial harness and locator failures and passing reruns are documented in the ledger. The Work fixture was restored; no disabled Work fixture package remains. Wi-Fi scanning, locale, theme, RTL and font settings were restored after testing.

| Screen | Before | After |
| --- | --- | --- |
| Main list | [before](../../../ux-review/before/01-main-list-light.png) | [after](../../../ux-review/after/phase9/01-main-list-light.png) |
| Selection bar | [before](../../../ux-review/before/05-selection-bar.png) | [after](../../../ux-review/after/phase3/05-selection-bar-work-light.png) |
| Confirmation | [before](../../../ux-review/before/07-uninstall-confirm-dialog.png) | [after](../../../ux-review/after/phase3/07-confirmation-work-expert-light.png) |
| App detail | [before](../../../ux-review/before/08-app-detail-top.png) | [after](../../../ux-review/after/phase5/08-overview-ad-privacy-light.png) |
| Filters | [before](../../../ux-review/before/02-filters-sheet.png) | [after](../../../ux-review/after/phase4/02-filters-light.png) |
| History | [before](../../../ux-review/before/12-history.png) | [after](../../../ux-review/after/phase7/12-history-light.png) |
| Device settings | [before](../../../ux-review/before/15-system-page.png) | [after](../../../ux-review/after/phase7/15-device-settings-light.png) |
| Compare | [before](../../../ux-review/before/14-compare-profiles.png) | [after](../../../ux-review/after/phase7/14-compare-light.png) |

Additional audit captures: [populated Privacy dashboard](../../../ux-review/after/phase7/13-privacy-dashboard-populated-light.png) and [populated RTL main list](../../../ux-review/after/phase9/01-main-rtl-arabic-complete.png). The full light/dark × Personal/Work × wallpaper-colour matrix and onboarding captures are indexed in [progress.md](progress.md).

## Decision to revisit

D6 asks the user to choose a profile on first run. It calls the existing `selectUser` path, so the choice applies immediately but Main returns to Personal on the next app launch. If the first-run choice is meant to become a persistent default, that needs a separate preference and migration decision. Component mutation/Undo also awaits a root-backed Shizuku test device; the current emulator cannot execute that path.

## Draft release notes

- Find the right apps faster with a compact inventory, profile-aware header, live counts and clearer filters.
- Review actions with specific risk information, then undo successful changes from the snackbar or History when safe.
- See live privacy and device-setting states at a glance, with clearer presets, comparison and diagnostics screens.
- Get a guided first run and improved large-text, touch-target, dark-theme and RTL support.
