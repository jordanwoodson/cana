# Privacy implementation progress

Owned task: Task 2 / C5 C6 C7 C10 U4 F2 F4. No device commands, commits, or external publication from this worker.

## Implemented interfaces

- `PrivacyOps.initialize()` binds desired state to `OperationalIdentity.current(context)`, a token stored in `noBackupFilesDir`. A missing or different token quarantines existing saved entries, strips reusable consent, and requires review.
- `PrivacyOps.markDisconnected()` persists disconnected status while preserving needs-review entries. Parent wired startup/reconcile/binder-death hooks.
- `PrivacyOps.reapplyDesired()` persists verified, needs-review, disconnected, or failed status. Consent requires matching package signing identity, first installation time, target user serial/id, UID, shared-UID peers, and exact reviewed warning keys. Missing legacy identity never automatically executes.
- `PrivacyOps.approvalKeys(pkg,user,reports)` includes target/user/group-bound shared-UID approval plus package/user-bound safety warning keys. Parent captures these during preset review.
- `PrivacyOps.forgetDesired(pkg,user,action)` journals and deletes only saved intent, with no package lookup or live UID write. Available for removed/replaced packages and offline.
- `PrivacyOps.restrict(..., selectedPermissions: Set<String>? = null)` validates requested mutable permissions and journals/restores only selected values. Unselected permissions remain untouched.
- Snapshots retain `ownerUserId`, `packageInstalledAt`, and sanitized `savedConsent`. Undo compares structural state via `SnapshotState`, validates the package installation, and restores earlier consent metadata (including needs-review state). Legacy consent restoration requires review.
- Adapter protocol: `cana-privacy --owner-user <Cana owner> <action> <package> <target user> <appId or permission names> [value]`. Optional sixth action argument on desired-set/metered-desired-set carries savedConsent. Legacy adapter calls without owner default to 0.
- `PrivacyDialog(pkg,user,onDismiss)` remains source compatible; `PrivacyDialog(pkg,user,embedded=true,onDismiss=...)` renders content inline. Mutations use authentication and application-owned batch execution.
- `PrivacyDashboardPage(onNavigateBack)` displays saved state separately from verified live state, profile, durable statuses, renewed review, technical details and safe Forget.
- Both Android backup formats use an allowlist of portable settings/presets/saved views. History, privacy intent, management inventory, operation_batches.pb, diagnostic files and reconciliation preferences are excluded from cloud and device transfer.

## Verification ledger

- Parent observed three policy regressions fail before implementation (`/tmp/cana-agent-red.log`), then all three pass.
- Parent observed store regression fail against no-op methods (`/tmp/cana-privacy-store-red.log`), then pass. Four initial tests and production compilation passed (`/tmp/cana-privacy-incremental.log`).
- Parent observed undo-renewed-consent regression fail (`/tmp/cana-final-recovery-red.log`), then pass in `/tmp/cana-undo-consent-red.log`. That run confirmed only the newly added undo-restriction consent test still failed; its policy/backend/UI implementation is now complete.
- Added `PrivacyImprovementsDeviceTest` acceptance scenarios for selected-permission isolation/exact undo, safe forget for nonexistent packages preserving peer intent, legacy intent requiring review without live mutation, and stable owner-routed profile identity.
- Added `PrivacyDisconnectedUiTest` for one explanation and working Connect/Retry actions. Updated existing Privacy UI/reboot assertions for readable labels and unified disconnected state.
- Parent owns centralized full JVM/build/lint/device execution. No runtime OEM or secondary-installation acceptance is claimed by this file.

## Remaining at this checkpoint

- `PrivacyOps.undo(record,batchId,approved)` and the dialog now require renewed peer-aware consent only when Undo reintroduces a restriction. Parent routes approvals from shared History/Undo flows; full-suite verification pending.
- Recovery worker implemented RestoreScript owner routing + savedConsent + no-op forget recovery, and history restore quarantine. Parent integrated initialization/disconnection hooks, preset shared-peer approval capture, embedded details and dashboard navigation.
- Centralized final green tests/build/lint and emulator acceptance, followed by source/requirement review.

## Final parent acceptance

Parent integration is complete. See [the final validation report](2026-09-26-audit-validation.md) for 156 passing JVM tests, passing debug/release builds and lint (zero errors), 28 distinct passing focused emulator tests, actual work-profile navigation, normal/enlarged-text inspection, retained evidence and explicit platform/performance limits. Earlier pending entries above describe historical checkpoints. No additional worker implementation remains.
