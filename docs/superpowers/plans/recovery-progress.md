# Recovery and journal implementation progress

Scope: Task 1 of the approved audit implementation. No device mutations or git commits performed by this worker.

## Implemented

- Installer results distinguish terminal failure from unknown outcome. A completion boundary retains late callbacks after timeout. Unknown results propagate through uninstall/cleanup, prohibit fallback and remain incomplete in the operation journal. Package mutations are blocked across profiles while the same package has an outstanding operation.
- `PackageOps.reconcilePending()` only reads state. A live request stays pending until its callback. A terminal callback plus actual state settles the result; after process restart, an unchanged state remains explicitly unresolved, while an observed target state can settle it. Explicit intended state is persisted before mutation, including component identity and the actual undo goal. Settled undo retains manual APK/data warnings and recovery-complete semantics. No reconciliation path repeats a privileged command.
- `PackageInstallerResult.onLateCompletion` lets CanaServices schedule read-only reconciliation after a callback.
- Safety warning keys include both user ID and target package; callers retain their set-based APIs.
- `SnapshotState` versions new snapshots, reads legacy flat snapshots, rejects unsupported schemas, and compares JSON values structurally (including explicit null and nested arrays/objects).
- Active history retains 500 resolved diagnostic records plus all unresolved/pending records and required undo markers. `HistoryArchive` stores cold records separately from active DataStore. `HistoryStore.allRecords()` feeds complete recovery exports. Monotonic sequence numbers preserve actual mutation order across archives even when the wall clock moves backward. `HistoryReadState` exposes unavailable storage safely to presentation while raw operation reads continue to fail closed.
- Package and privacy/system journals share one `HistoryStore.mutationMutex`. The lock graph was reviewed: mutation closures do not recursively enter the journal, and privacy undo filters desired-state provider calls before executing its remaining platform commands.
- `HistoryStore(..., installationId = OperationalIdentity.current(context))` hides restored records immediately; binding moves them to `quarantinedRecords` for read-only review. Constructor-compatible defaults preserve tests and callers; production supplies the archive and installation identity.
- Preset capture preserves desired metered intent while enforcement is pending and avoids inventing permission restriction when all permissions are immutable.
- Exported recovery routes the Cana APK lookup and recovery adapter through the recorded installation owner, separately from the package target profile; desired-state commands preserve consent metadata; forgotten identities are never recreated by recovery.

## Regression evidence

Parent executes Gradle centrally because worker escalation calls stalled awaiting approval. The initial focused run confirmed both RecoveryAuditTest failures. `/tmp/cana-final-recovery-red.log` then recorded 19 tests / 8 failures: three installer-uncertainty failures, two preset-capture failures, owner-routing failure, unknown-schema failure, and the privacy worker's regression. Structural comparison and bound approval tests passed after implementation.

Further parent-observed regression cycles: `/tmp/cana-archive-order-red.log` (18 tests, only archive-order regression failed), `/tmp/cana-full-integration-check.log` (149 tests, only unreadable-post-state uncertainty regression failed), `/tmp/cana-intended-state-red.log` (7 tests, new component/undo goal tests failed), `/tmp/cana-settlement-red.log` (9 tests, new preflight/manual-undo settlement tests failed). Their corresponding fixes are now implemented; production/test files are frozen for the parent's final complete acceptance run. Scoped `git diff --check` passes.

Final central validation passed: **156 JVM tests, zero failures/errors; debug and release builds; debug and release lint, zero errors** (`/tmp/cana-final-validation.log`, generated JUnit XML). Lint reported 449 warnings, predominantly missing translations. The central build used the concurrently updated Gradle 9.8 wrapper, which this worker preserved. Device acceptance is owned by the parent and is not claimed by this worker.

## Integration hooks

```kotlin
HistoryStore(appContext.historyDataStore,
    HistoryArchive(File(appContext.noBackupFilesDir, "history-archive")),
    installationId = OperationalIdentity.current(appContext))
history.bindInstallation(installationId)
packageOps.reconcilePending()
PackageInstallerResult.onLateCompletion = { onSystemEvent() }
RestoreScript.generate(history.allRecords(), UserProfile.currentUserId)
```

`OperationResult.outcomeUnknown` must remain distinct from success/failure; `BatchResult.unknownCount` counts it and `failureCount` excludes it. Cold archive writes complete before records leave active DataStore; failed archive writes keep the journal recoverable.

## Honest limits

A process death can lose an Android callback. Unchanged live state does not prove that the outstanding request stopped, so such records remain pending for review instead of being falsely closed or automatically retried. The package gate also blocks in-app undo in this situation; reconnect alone cannot necessarily settle it. This conservative limit is retained deliberately, and no boot-detection/acknowledgement shortcut was added during final acceptance. Real OEM timing/callback behavior still needs emulator/physical-device verification beyond the pure completion and reconciliation tests.

## Final acceptance: hidden API initialization

The parent observed profile enumeration fail after forced AOT compilation, with `NoSuchElementException` in `ShizukuUserUtils.getUsers()` and a passing rerun after compilation reset. Read-only inspection of the actual HiddenApiBypass 6.1 runtime jar and its tagged upstream source confirmed that `addHiddenApiExemptions` mutates a plain shared `HashSet`, snapshots it without synchronization, ignores the return of `toArray`, and replaces the VM exemption list. Concurrent profile/package initialization can therefore lose prefixes. The timing connection to forced compilation remains a hypothesis until the same compiled environment is retested.

Minimal fix: `HiddenApiAccess.ensureReady()` registers the full fixed prefix set through a checked, synchronized initializer, once per process after success. Failed initialization stays retryable. All app and instrumentation exemption-update sites use that helper; the reflection discovery implementation was deliberately left unchanged to isolate this fix. Profile restriction query failures now propagate to fail-closed safety checks instead of returning false. Existing UI profile-query callers catch/report failures; History retains numeric IDs when friendly profile names are unavailable.

Parent observed `/tmp/cana-hidden-api-red.log`: **2 tests, 2 assertion failures** before the readiness latch (concurrent registration once; failed-then-successful registration). Final central green validation then passed: **156 JVM tests, zero failures/errors/skips, debug and release builds and lint passed, 449 warnings / zero errors** (`/tmp/cana-final-validation.log`). Forced-AOT `SafetyDeviceTest` and privacy adapter identity acceptance passed on the final APK: 2/2 in 42.637 seconds (`/tmp/cana-final-compiled-startup.log`). Production code is frozen; no reflection fallback was added.
