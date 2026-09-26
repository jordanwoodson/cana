# Cana audit improvements

The user approved implementing every recommendation in the 2026-09-26 audit. This document preserves that complete scope. The existing report supplies the product brief: make privileged actions understandable, profile-correct, and recoverable, then add review and comparison tools. Implementation is authorized; no further design approval or publishing is required.

## Constraints

- Keep native Kotlin/Jetpack Compose, Android 9/API 28 minimum, the current application ID and signing identity.
- Preserve immutable package/profile targets, authentication, journal-before-mutation, actual platform verification, offline operation, and protected-package checks.
- Do not operate on physical devices, publish, change signing secrets, or silently apply saved presets.
- Use emulator fixtures for mutations. UI observation can use an isolated read-only emulator.
- Keep unsupported capabilities explicit. Unknown state must never appear as success or as unrestricted/blocked without evidence.

## Required outcomes

### Correctness (C)

C1. Selection is independent of filters/search; total and hidden counts remain visible; Select All is derived/tri-state; every target is reviewable.
C2. Installer timeout is a distinct unknown outcome. Retain pending recovery and reconcile eventual state. Never start destructive fallback while an earlier operation remains outstanding.
C3. Safety approvals bind user, target package, and warning, including presets, undo, privacy peers, and OTA.
C4. A screen leaving composition/rotation cannot silently truncate batches. A surviving batch owner exposes progress, results, explicit stop-between-items, and durable planned-item state for interruption reporting/review. Process restart does not silently re-execute privileged operations.
C5. Reconciliation surfaces durable needs-review/failure status. Unchanged consent is reusable only when identity and reviewed safety conditions match.
C6. Operational state is excluded from backup and transfer; portable presets/settings remain portable. Legacy restored data must not silently execute as fresh operational intent.
C7. Privacy helpers/recovery carry the Cana installation owner separately from the package target user.
C8. Android App info opens the selected user's package or explains unsupported access.
C9. All mutation paths invalidate shared inventory, including presets, History, OTA, privacy and components as relevant.
C10. Saved restrictions can be forgotten safely after package deletion/UID change, without touching the old live UID.
C11. Presets consistently preserve desired metered/network intent even when live enforcement is pending.

### Experience (U)

U1. Installed/Removed tabs have visible localized labels; active profile remains visible during search and before a switch.
U2. Persistent selection action bar shows total/hidden targets and profile, Clear and Review actions.
U3. Filter sheet separates sort, state/risk/category; removable active chips and saved views.
U4. One clear disconnected-service state with Connect/Retry actions replaces repeated raw errors. Technical diagnostics are expandable. Privacy states use readable status labels.
U5. Risk badges have legible larger text, accessible theme-aware colors, localized labels and nonduplicated semantics.
U6. Dedicated app details screen preserves profile and offers Overview, Privacy, Components and profile-correct Android App info.
U7. History groups operations by batch, with readable actions/profile names and outcome/recovery counts; snapshots remain expandable diagnostics.
U8. Empty states distinguish no results, no apps, unavailable service and failure; appropriate Clear filters/Retry actions.

### Maintenance (M)

M1. One profile-aware inventory repository replaces companion-object app state and scattered refresh handling.
M2. Presets initialize/collect once using application context.
M3. Introduce injectable boundaries where necessary for delayed installer, cancellation, identity and safety tests.
M4. Operational snapshots compare typed/structural values, with explicit schema versioning and legacy readers.
M5. Bound diagnostics and archive completed history while retaining unresolved recovery records.
M6. Render cached/bundled recommendations before a background refresh.
M7. Custom list URLs use explicit validated Save; editing alone does not change persisted settings/cache metadata.
M8. Remove dead UI/resources and obsolete Gradle options; improve localization/accessibility and retain honest limits for untranslated content.
M9. CI runs JVM tests, lint and focused emulator regressions for interruption, profile switching, offline/disconnected state and safety/recovery edge cases; scoped workflow permissions.
M10. Update privacy policy and user documentation to describe downloads, local operational data, exports, backup and new controls accurately.

### Features (F)

F1. Preflight change plan: exact apps/profiles/actions, shared effects, data-loss and recovery limits, unavailable/skipped targets, authentication and a final explicit execute action.
F2. Privacy dashboard: saved versus verified state, disconnected status, reapply failures, renewed consent and forget actions.
F3. Profile comparison: read-only matrix of installed/removed/disabled/suspended/privacy states across chosen profiles, explicit unavailable cells.
F4. Per-permission controls: revoke selected mutable permissions without changing unselected permissions; exact recovery and consent checks preserved.
F5. Preset import/apply preview and diff: added/removed targets, privacy changes, missing packages, profile mismatch; no writes or actions before review.
F6. Durable named saved filters/collections, optionally profile-scoped, using stable identifiers rather than translated labels.

## Architecture and verification

Application-owned repositories coordinate inventory, operation batches and privacy status. Screens observe state; operation inputs remain immutable. Small policy functions and injected platform boundaries support JVM regression tests; device tests exercise the actual UI and platform. No test should merely grep source or copy implementation formulas.

Each requirement needs a source reference and relevant passing test or visible emulator evidence in the progress ledger before completion. Verify debug/release builds and lint, JVM suite, emulator regressions, disconnected mode, rotation, enlarged text and navigation. State OEM/physical-device limits honestly rather than claiming universal platform compatibility.
