# Cana audit implementation and validation

Scope: the approved [audit design](../specs/2026-09-26-audit-improvements-design.md) and [implementation plan](2026-09-26-audit-improvements.md). Baseline: `232fdbd`, branch `roadmap`. All approved audit outcomes are implemented and locally verified within the limits below. This report records the implementation acceptance checkpoint; subsequent publication is covered by the [3.2.2-cana.4 release notes](../../releases/3.2.2-cana.4.md).

## Delivered behavior

- Package actions use an explicit plan with all selected targets, including hidden, unavailable, and excluded apps. Profile identity, shared effects, data-loss limits, and required consent remain visible before execution.
- Application-owned batches continue across navigation and recreation, expose progress and stop-between-items, and persist interrupted plans without replay. Installer uncertainty stays distinct from failure and success, and conflicting actions are held until verification.
- Privacy has a dashboard, readable saved-versus-live status, selected-permission controls, identity-bound consent, renewed review, and a safe Forget action for removed/replaced apps. Backup includes portable settings, presets, and saved views; operational history and restrictions are excluded.
- Dedicated app details preserve the selected profile across Overview, Privacy, Components, and Android App info. Profile comparison is read-only. Named filter views and package collections can be global or profile-scoped.
- Preset import reviews a diff before writing. Preset apply reviews missing targets, profile mismatch, package and shared-UID effects. Cancel preserves saved content.
- History groups batch outcomes and recovery information, preserves unresolved records, archives older completed records for export, and quarantines history from a different installation. Logs are bounded; custom list URLs require validated Save.
- Dead dialogs, controls, Flutter resources, unused strings, and obsolete Gradle options were removed. CI runs unit/lint/build checks and isolated emulator regressions with scoped permissions. README, privacy policy, usage, presets, settings, and feature documentation were updated.

## Requirement evidence

Sources below are relative to `app/src/main/java/io/github/samolego/canta/` unless a full repository path is shown. JVM test classes are under `app/src/test`; device classes under `app/src/androidTest`.

| Requirement | Implementation | Verification |
|---|---|---|
| C1, U2 | `ops/SelectionPolicy.kt`, `ui/component/SelectionActionBar.kt`, immutable `PackageActionRequest` | SelectionPolicyTest; SelectionUxDeviceTest hidden/review/clear and captured targets |
| C2 | `util/PackageInstallerResult.kt`, `ops/PackageOutcome.kt`, `ops/PendingPackageRecovery.kt`, update/uninstall sequences | InstallerUncertaintyTest, PendingPackageRecoveryTest, batch pending test; delayed callback, failed readback, no overlapping fallback, vanished target |
| C3 | `ops/SafetyPolicy.kt`, `ops/SafetyInspector.kt`, privacy approval keys, package/preset/Undo review | SafetyPolicyTest role transfer; PrivacyImprovementsPolicyTest; fresh independent integration review |
| C4 | `ops/BatchCoordinator.kt`, `data/BatchStore.kt`, global progress banner | BatchCoordinatorTest cancellation/stop/storage failure/restart; BatchLifecycleDeviceTest recreation, reopening storage, late callback race |
| C5 | `ops/PrivacyOps.kt`, `ops/PrivacyReviewPolicy.kt`, `data/PrivacyStore.kt` | PrivacyImprovementsPolicyTest, PrivacyReviewStoreTest; consent renewal/undo and durable needs-review |
| C6 | `ops/OperationalIdentity.kt`, HistoryStore/PrivacyStore binding; both backup XML allowlists | HistoryIsolationTest, PrivacyReviewStoreTest; source review of cloud and transfer rules |
| C7 | `ops/PrivacyPlatform.kt`, `ops/PrivacyRecovery.kt`, ShellUserService and RestoreScript owner routing | RecoveryOwnerTest executes generated shell with independent owner/target adapters; PrivacyImprovementsDeviceTest identity adapter |
| C8 | `ops/AppTarget.kt`, `ui/screen/AppDetailPage.kt` | AppTargetTest explicit user argument, invalid identity rejection; runtime screen inspection |
| C9, M1 | `ops/InventoryRepository.kt`, CanaServices history/batch observers | Inventory invalidation runs after attempts, including persistence failure; cached profiles invalidated for shared changes; peer review |
| C10 | `PrivacyOps.forgetDesired`, Privacy dashboard/dialog | PrivacyReviewStoreTest; PrivacyImprovementsDeviceTest missing package and peer intent |
| C11 | `ops/PresetOps.kt` desired metered/network capture | PresetCaptureTest pending metered intent and fixed-permission exclusion |
| U1 | `ui/component/AppTopBar.kt`, `ui/CantaApp.kt` | Visible Installed/Removed labels and persistent profile; normal and enlarged-text inspection |
| U3, F6 | `ui/menu/FiltersMenu.kt`, SavedView/SavedViewStore, AppListViewModel | SavedViewTest round trips/scope; CategoryFilterDeviceTest; SelectionUxDeviceTest actual scoped/global profile transitions and usage availability |
| U4 | PrivacyDisconnectedNotice, privacy status labels/expandable diagnostics | PrivacyDisconnectedUiTest single explanation and working Connect/Retry callbacks |
| U5 | AppBadge, AppTile semantics/resources | Larger theme-contrast text, localized risk/state labels, named selection controls; screen inspection |
| U6 | AppDetailPage and embedded privacy/components | Profile-correct navigation; source and runtime review |
| U7 | HistoryPage and HistoryTimeline | HistoryTimelineTest grouping, legacy records, unstarted plans; profile names/IDs, outcome/recovery counts and quarantine review |
| U8 | AppList/InventoryState | Separate filtered-empty, empty, unavailable, stale/error states; selection review remains available with no visible app |
| M2 | PresetsViewModel application context and guarded initialization | PresetStoreTest; preset import UI tests |
| M3 | InstallerCompletion, BatchCoordinator callbacks, pure review/identity policies | Real timeout/callback, cancellation, restored identity, role-transfer and persistence failure regressions |
| M4 | `ops/SnapshotState.kt`; package/privacy/system verification | RecoveryAuditTest structural order and unknown schema; SystemOps uses structural comparison |
| M5 | BoundedLogBuffer, HistoryArchive and HistoryStore retention | BoundedLogBufferTest, HistoryRetentionTest, HistoryIsolationTest monotonic archive order; unresolved records stay live |
| M6 | InventoryRepository cached/bundled data before metadata refresh | AppListLoadingDeviceTest cancellation/return, cached apps visible before remote loader finishes |
| M7 | BloatListUrlPolicy/editor and Settings UI | BloatListUrlPolicyTest validation, Cancel/Save, write failure and draft isolation |
| M8 | Resource/locale repairs, obsolete UI/Gradle cleanup | Debug/release lint: zero errors; untranslated resources remain honest warnings |
| M9 | `.github/workflows/pull_request_build.yml`, build/release workflows | Local commands pass; CI emulator selection includes profiles, disconnected UI, recreation/restart/recovery; remote workflow not run here |
| M10 | README, privacy_policy.md, docs/features.md, presets.md, settings.md, usage.md | Source-aligned review of downloads, storage, backup, recovery limits and new workflows |
| F1 | PackageActionDialogs, PresetApplyDialog, UndoBatchDialog, OTA review | Immutable target/preflight review; UI tests and independent review; safety rechecked during execution |
| F2 | PrivacyDashboardPage | Saved/verified/disconnected/failed/needs-review labels and review/forget actions; privacy policy/store/device coverage |
| F3 | ProfileComparisonPage and ProfileComparison | ProfileComparisonTest joins, absent packages/profiles, explicit unavailable cells |
| F4 | PermissionChoices and PrivacyOps selected-permission journal/undo | PrivacyImprovementsPolicyTest and PrivacyImprovementsDeviceTest exact selected undo with peer permissions unchanged |
| F5 | PresetImportReviewDialog, PresetPreview, PresetStore | PresetJsonTest/PresetPreviewTest/PresetStoreTest; import Cancel and Update UI acceptance |

## Build evidence

`/tmp/cana-final-validation.log`: **156 JVM tests passed**, debug and release APK builds passed, debug and release lint passed. Both lint variants report **449 warnings and zero errors**. Most warnings are untranslated resources (375); remaining categories include unused resources, plural candidates, dependency notices and platform/style guidance. No blanket new lint suppression was added.

The Gradle wrapper changed concurrently from 9.3.1 to 9.8.0 outside this audit worker's edits. That change was preserved. Final checks use the current 9.8.0 wrapper. `git diff --check` is clean for audit-owned files; the regenerated Windows wrapper has CRLF whitespace notices.

Emulator acceptance: the isolated UI/lifecycle suite passed **13/13** in 174.959 seconds (`/tmp/cana-final-ui-acceptance.log`). The first privileged fixture run passed 11 of 13; it exposed the app-op parser issue and stale UI result assertion described below. After correction, the focused privacy, preset UI, authentication and component/package recovery run passed **5/5** in 528.919 seconds (`/tmp/cana-final-fixture-normal-runtime.log`). Across those runs, **28 distinct device tests passed**. The final startup regression and screenshot evidence are recorded below.

The build and acceptance logs, plus selected screenshots, are retained in [the evidence folder](../evidence/2026-09-26/).

## Limits

- Verification uses a disposable Android 15 emulator with shell-backed Shizuku. No physical device, OEM-specific framework, root-backed Shizuku, real OTA image, or public release was exercised.
- The unaccelerated software emulator produced cold-launch ANRs, including after APK precompilation. Android reported `No response to onStartJob`; the latest main-thread trace waits in `HardwareRenderer.setStopped` / the render thread. An earlier trace showed active Compose rendering. Screens were inspected after choosing Wait and allowing rendering to settle. These results are functional acceptance, not real-device performance certification; physical-device launch profiling remains a release follow-up.
- If a process loses an installer callback and the requested state never appears, Cana conservatively keeps the record pending and blocks conflicting retry/Undo. It does not infer that Android canceled the operation. Recovery may need manual review.
- Undo cannot recreate deleted app data or a missing APK payload. Metered/network enforcement after reboot depends on Shizuku and Android scheduling. Unknown/unavailable state remains explicit.
- New text has English fallback; complete translation coverage is not claimed. History from an unbound/restored installation is visible for review but cannot drive automatic recovery.

## Additional acceptance findings

- Device acceptance exposed Android’s two-line empty app-op response. PrivacyPolicy now accepts the reported operation default while preserving an explicit `default` mode as a distinct value. The legacy one-line form uses the background operation’s allowed mode. This follows [AOSP’s shell output](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/appop/AppOpsService.java#5716) and [operation default](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/AppOpsManager.java#2919). A recorded-output JVM regression was observed failing and then fixed; the typed privacy/exact Undo device regression passed in `/tmp/cana-final-fixture-normal-runtime.log`.
- Component toggles now use the shared authentication and durable batch path, and component Undo uses the reviewed Undo dialog. Both review dialogs display package, component and profile identity.
- The older preset-apply UI acceptance assertion was updated for the new four-part result counts. Its fixture cleanup now runs in `finally`, including on assertion failures, and each run uses a unique preset identity so prior failed-run fixture data cannot cause a collision.

## Manual History acceptance

The normal app History flow was used to restore the fixture restriction left by the older UI assertion failure. The review displayed `io.github.jordanwoodson.cana.fixture`, metered background data, user 10. Confirming Undo produced one verified success, zero failures/skips/pending results, and changed the original batch to one restored / zero changes not restored. Screenshots: `/tmp/cana-final-history.png`, `/tmp/cana-final-undo-review.png`, `/tmp/cana-final-undo-complete.png`.

## Startup reflection finding

Forced emulator precompilation exposed a profile lookup failure: ordinary reflection could no longer see `IUserManager.getUsers`. The safety gate refused mutation. The failing run is `/tmp/cana-final-fixture-rerun.log`; resetting compilation allowed the existing profile safety acceptance to pass (`/tmp/cana-profile-reflection-reset.log`). Inspection of the cached HiddenApiBypass 6.1 bytecode found unsynchronized mutation and replacement of a shared exemption set. The app now registers its complete required prefix set through one checked, synchronized initializer per process; profile restriction query failures propagate instead of implying no restriction. Two concurrency/failure regressions failed before the guard (`/tmp/cana-hidden-api-red.log`). The [upstream implementation](https://github.com/LSPosed/AndroidHiddenApiBypass/blob/main/library/src/main/java/org/lsposed/hiddenapibypass/HiddenApiBypass.java) documents the same replacement API. Precompilation changing the race timing is an inference; the final APK passed both SafetyDeviceTest and the privacy adapter identity test after forced precompilation (2/2 in 42.637 seconds; `/tmp/cana-final-compiled-startup.log`).

## Profile navigation acceptance

The normal UI was used to select Work profile (user 10), search for the fixture, and open its Overview. App info launched Android Settings with `topResumedActivity ... u10 com.android.settings/.spa.SpaActivity`; the activity record also reports `userId=10`. This verifies the actual destination, not only the command builder. Screenshots and the activity record are in the evidence folder.

## Enlarged-text and screen review

The emulator was inspected at font scales 1.0 and 1.5 (540×1200). Work-profile context stayed visible in search and app details. Overview review buttons, selected-permission controls, live background/metered/network labels, component switches, grouped filters and saved-view controls remained reachable by scrolling. The privacy dashboard correctly showed no saved restrictions after fixture cleanup. Profile comparison displayed separate Owner/user 0 and Work/user 10 columns, including Installed versus Removed, and horizontal scrolling exposed the second column.

The detail tab row initially broke “Components” across a word at 150% text. It was changed to a horizontally scrolling Material tab row with single-line labels. The rebuilt APK was inspected at 150% text: labels stay on one line and horizontal scrolling reveals the complete Components label. Font scale was restored to 1.0 after inspection.

Selected evidence:

- [Work-profile search](../evidence/2026-09-26/cana-fixture-search-final.png)
- [App details and captured profile](../evidence/2026-09-26/cana-details-work-final.png)
- [Actual Android work-profile App info](../evidence/2026-09-26/cana-work-app-info-ready.png)
- [Large filters](../evidence/2026-09-26/cana-filters-large.png) and [saved-view controls](../evidence/2026-09-26/cana-filters-large-lower.png)
- [Large privacy dashboard](../evidence/2026-09-26/cana-dashboard-large.png)
- [Large comparison](../evidence/2026-09-26/cana-comparison-large-ready.png) and [second profile column](../evidence/2026-09-26/cana-comparison-large-scrolled.png)
- [Reviewed Undo](../evidence/2026-09-26/cana-final-undo-review.png) and [verified recovery](../evidence/2026-09-26/cana-final-undo-complete.png)

- [Refined large-text tabs](../evidence/2026-09-26/cana-tabs-large-fixed.png) and [fully revealed Components label](../evidence/2026-09-26/cana-tabs-large-scrolled-fixed.png)
