# Cana Audit Improvements Implementation Plan

> For agentic workers: use the planning, test-driven development, parallel dispatch and verification skills. Checkboxes track the complete approved audit scope.

**Goal:** Implement all recommendations from the approved audit, including all six features.
**Architecture:** Keep native Compose; centralize inventory and batch ownership in application services. Preserve existing operation APIs where possible and introduce explicit state models for uncertainty, safety consent and privacy verification.
**Tech Stack:** Kotlin, coroutines/StateFlow, Compose Material 3, protobuf DataStore, Shizuku, JUnit and Android instrumentation.
**Spec:** `docs/superpowers/specs/2026-09-26-audit-improvements-design.md`

## Global constraints

- Android 9/API 28 minimum; existing application ID/signing identity; native Compose.
- Journal before mutation, verify afterward, preserve authentication/profile targeting and protected packages.
- No publishing or physical-device mutations. No automatic replay of interrupted batches.
- Exact user and package identities accompany all approvals and operations.

## Review focus

- Delayed installer completion must retain recovery and prohibit overlapping fallback (Task 1).
- Role transfer/shared UID changes must invalidate unrelated consent (Tasks 1–2).
- Rotation, navigation and process restart must expose complete/partial batch intent (Task 4).
- Restored data and changed UID must not silently apply or strand restrictions (Task 2).
- Hidden selections, profile changes, missing packages and partial access must remain obvious before execution (Tasks 3–7).

## Task 1: Installer outcomes, approval identity, journal maintenance

Files: `ops/PackageOps.kt`, `util/PackageInstallerResult.kt`, `ops/UpdateCleanupSequence.kt`, `ops/PackageOutcome.kt`, `ops/SafetyPolicy.kt`, `ops/SafetyInspector.kt`, `ops/RestoreScript.kt`, `data/HistoryStore.kt`, `ops/PresetOps.kt`, associated tests.
Interfaces: retain existing constructors with defaulted additional fields; represent unknown outcome explicitly. Safety warning keys include target package at their construction; callers can continue passing sets without cross-target collisions. Keep legacy history readable. Expose reconciliation to parent application services.

- [x] Add failing regressions for delayed/unknown outcomes, fallback exclusion and role-transfer approval isolation.
- [x] Implement C2/C3, structural schema-aware snapshots (M4), retention of unresolved history (M5), desired metered capture (C11).
- [x] Run focused tests then full JVM suite; document exact behavior and integration hook.

## Task 2: Privacy identity, reconciliation, permission choices and dashboard

Files: `ops/PrivacyOps.kt`, `ops/PrivacyPlatform.kt`, `ops/ShellUserService.kt`, `ops/PrivacyRecovery.kt`, `data/PrivacyStore.kt`, `privacy.proto`, backup XML, `ui/dialog/PrivacyDialog.kt`; create `ui/screen/PrivacyDashboardPage.kt` and privacy-specific resources/tests.
Interfaces: dashboard composable `PrivacyDashboardPage(onNavigateBack: () -> Unit)`. Preserve existing restrict/undo APIs with optional selected permission names. Expose durable status and safe forget through PrivacyOps. Coordinate changes to `CanaServices`/`RestoreScript` with parent/Task 1 owner.

- [x] Add failing regressions for consent review, changed UID forgetting, installation owner routing and selected permissions.
- [x] Implement C5/C6/C7/C10, U4, F2/F4 and readable status UI.
- [x] Verify pure policies/stores, add device acceptance coverage, report required navigation hooks.

## Task 3: Inventory, selections, filters and saved collections

Files: `ui/viewmodel/AppListViewModel.kt`, `ui/component/AppList.kt`, `ui/component/AppTopBar.kt`, `ui/menu/FiltersMenu.kt`, `ui/CantaApp.kt`, `util/apps/Filter.kt`; new inventory and saved-view repositories/policies.
Interfaces: application-owned per-profile inventory state and revision invalidation; selection remains independent of views; stable filter IDs and named persisted saved views.

- [x] Add failing tests for hidden-selection counts, tri-state selection and saved filter round trips/profile scope.
- [x] Implement C1/C9, M1/M6, U1/U2/U3/U8, F6.
- [x] Verify switching profiles and filtering on the emulator without executing an action.

## Task 4: Durable batches and preflight plans

Files: new `ops/BatchCoordinator.kt`, batch store/policies, `ui/dialog/PackageActionDialogs.kt`, `ui/dialog/preset/PresetApplyDialog.kt`, `ops/UndoCoordinator.kt`, `ui/component/OtaBanner.kt`, `ui/screen/HistoryPage.kt`, `ops/CanaServices.kt`.
Interfaces: coordinator owns work in application CoroutineScope; persisted planned/started/completed/canceled/interrupted item state. Current item journals before stopping; process startup marks unfinished work for review without auto-replay. All mutating entry points invalidate inventory.

- [x] Add failing tests for cancellation between items, detached observers/rotation, interrupted restart and immutable targets.
- [x] Implement C4, U7, F1 and integrate tasks 1–3.
- [x] Exercise package/preset/undo batch lifecycle and outcome reporting; full JVM suite.

## Task 5: Preset lifecycle and preview/diff

Files: `data/PresetStore.kt`, `ui/viewmodel/PresetsViewModel.kt`, `ui/screen/PresetsPage.kt`, import/edit dialogs; new preset preview policy and UI.
Interfaces: preview is read-only until explicit save/apply, UUID collision behavior is reviewed and preserves old preset until confirmation. Apply dialog/coordinator integration belongs to Task 4; report preview inputs for it.

- [x] Add regressions for import diff, missing/profile-mismatched targets and initialization lifecycle.
- [x] Implement M2/F5 including added/removed package/privacy changes and explicit confirmation.
- [x] Verify import cancel leaves presets unchanged, confirm persists exact reviewed content.

## Task 6: App detail screen, readable controls and profile comparison

Files: `ui/CantaApp.kt`, navigation, app details/components/privacy UI, badges; new `ui/screen/ProfileComparisonPage.kt` and comparison policies.
Interfaces: App detail route carries immutable package/user IDs. Comparison reads the shared inventory/privacy repositories with unavailable states and no mutations.

- [x] Add tests for identity routing, comparison joins and missing/unavailable profiles.
- [x] Implement C8, U5/U6, F3; preserve accessibility focus/labels and readable diagnostics.
- [x] Inspect navigation, profile context, large font layouts and supported actions on emulator.

## Task 7: Settings, diagnostics, resources, CI and documentation

Files: settings screen/store, `util/LogUtils.kt`, dead UI/resources, Gradle properties, `.github/workflows/*`, privacy policy/README/docs.

- [x] Add URL validation/save and bounded-log tests where behavior changes.
- [x] Implement M3/M5/M7/M8/M9/M10; improve localization and avoid new hardcoded UI copy.
- [x] Run JVM suite, debug/release build/lint and focused emulator tests; inspect UI in normal/large font and disconnected state.

## Task 8: Whole-change review and requirement audit

- [x] Obtain a fresh review covering spec and actual diff; resolve correctness findings with regressions.
- [x] Record source/test/screenshot evidence for every C/U/M/F requirement in progress ledger.
- [x] Leave full goal active until every required implementation is complete and verification is sufficient.

## Execution decisions

Implementation was explicitly authorized after the detailed report. Use this existing clean `roadmap` branch/workspace, with parallel workers on independently owned files and parent integration; do not create redundant approval gates or publish. Keep all work reviewable in the worktree; commits are optional until integration is verified.

## Completion

All eight tasks are implemented and reviewed. Final validation: 156 JVM tests, debug/release APK builds, both lint variants (zero errors; 449 warnings), and 28 distinct focused emulator tests across recorded runs. Forced-compilation profile safety/identity acceptance passed; normal and 150% text navigation was inspected. See [the final validation report](2026-09-26-audit-validation.md) for requirement mapping, screenshots, exact logs and remaining platform/performance limits. Work remains uncommitted and unpublished.
