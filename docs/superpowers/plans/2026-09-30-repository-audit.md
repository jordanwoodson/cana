# Repository audit implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement the tasks in this workspace. The user authorized fixes and improvements; no additional design approval is needed.

**Goal:** Review the entire repository for bugs, omissions and performance/UX improvements, repair Shizuku authorization, show APK/update sizes in the app list, and research more comprehensive tracker sources.

**Architecture:** Preserve the existing Compose, application-owned operation and repository structure. Give Shizuku authorization an application-owned, observable request state with no retained UI callbacks. Keep measurements and tracker results explicit about their limitations.

**Tech stack:** Kotlin, Android API 28+, Compose, Shizuku, Gradle, JUnit and Android instrumentation.

**Spec:** The active user objective, reproduced in Goal above. Earlier September 26 acceptance is historical evidence, not proof of this audit's completion. Baseline `a594782`, initially clean tree.

## Constraints and review focus

- Keep profile identity and existing mutation/recovery protections intact; do not publish or mutate a physical device.
- Test repeat taps, denial, binder death/reconnect, delayed callbacks, request exceptions/timeouts, dismissal and recreation.
- APK sizes include unique split APKs; updated system APK bytes are already included in active APK bytes. Never imply these figures include data/cache or the factory image.
- Tracker review must cover source breadth/licensing, partial scans, outdated cache, cancellation and malformed APK/resource limits.
- Complete the broad scan and source research before claiming the original goal complete.

## Tasks

### 1. Shizuku authorization

- [x] Trace existing request path and compare with upstream Shizuku API source.
- [x] Add `ShizukuAuthorizationTest` regressions for request identity, duplicate taps, rejection, disconnect/reconnect, late results and exceptions.
- [x] Implement `util/shizuku/ShizukuAuthorization.kt`, a JVM-testable state machine consuming a platform snapshot and request function.
- [x] Adapt `ShizukuPermission.kt` with process-owned listeners, bounded request wait, safe live status reads; retain no UI callbacks.
- [x] Update `ShizukuRequirementDialog.kt` and callers: collect live state, refresh on resume, show actionable failures, disable unavailable actions, scroll at large text sizes, and complete once on grant.
- [x] Verify core regressions and UI lifecycle/action tests, then compile/lint.

### 2. App list sizes

- [x] Review measurement paths and add failure/unique-split regressions to `ApkSizesTest`.
- [x] Update `AppTile.kt` to show labeled APK bytes and updated system APK bytes, with unavailable values explicit.
- [x] Verify regular/updated/removed app rows, wrapping and semantics; document measurement meaning.

### 3. Repository and tracker audit

- [x] Review inventory/filter/selection/profile behavior and performance.
- [x] Review package actions, shell boundaries, safety, privacy, presets and recovery persistence.
- [x] Review tracker parsers, caches, scan completeness, network clients and source update paths.
- [x] Research current primary tracker sources, compatibility and licensing; implement justified improvements.
- [x] Review remaining UI/navigation, resources, backup/manifest, build/CI/scripts and docs.
- [x] Record findings, fixes and explicit verification limitations.

### 4. Acceptance

- [x] Run all JVM tests, debug/release builds, Android test compilation and lint.
- [x] Run focused emulator authorization/size/tracker acceptance and inspect rendered UI if an emulator is available.
- [x] Map each original requirement to current source and verification evidence.

## Findings and evidence

- Existing authorization adds a new permanent result listener on each tap, retains UI callbacks, closes setup on denial, trusts a cached binder flag, and does not refresh dialog status on resume. `RequirementItem` dims disabled items but leaves their buttons clickable and uses 32dp targets.
- `AppInfo` already measures APK and update bytes, but `AppTile` shows neither. APK measurement currently has no exception handling.
- Tracker scanner can report success after skipping oversized DEX or unreadable split APKs; source-change/cache races and freshness need investigation.
- Initial Gradle command was blocked by the read-only cache; the interrupted escalation had no live process handle. Baseline verification restarted under escalation after the user said proceed; log `/tmp/cana-audit-baseline.log`.

## Initial implementation checkpoint (superseded by final acceptance below)

- Authorization: process-owned state/listeners, unique request IDs, repeat-tap guard,
  60-second timeout, live status checks on binder events/resume, explanatory denial
  and blocked states, no retained UI callbacks. All explicit connection flags and
  the dashboard's nested review target are saved across recreation. The dialog is
  scrollable and uses 48dp named actions. Downloads now use Shizuku's official
  download page; the old SDK check sent Android 12+ users to an Android-16 fork.
- APK sizes: list and Overview show base+unique splits and included update bytes.
  Missing/unreadable files invalidate the whole size; banner totals preserve unknown.
- Independent review identified and fixed unsaved connection flags, an unsaved
  nested dashboard review target, and false-exact banner totals. Late callbacks
  now refresh live state without trusting an expired result code.
- Nine authorization and five size JVM tests, including red/green regressions.
  All 186 JVM tests passed; zero skipped. Both APK builds and both lint variants
  passed; 487 warnings (mostly translations), zero errors. Final recheck passed
  after the nested dashboard target fix; log: `/tmp/cana-audit-reviewed-validation.log`.
- Five initial content UI tests passed. The actual MainActivity recreation test
  failed at its post-recreation assertion before the flag fix
  (`/tmp/cana-shizuku-lifecycle-red.log`) and passed in the six-test rerun (6/6 in 137.509 seconds).
  Final suite log: `/tmp/cana-audit-final-ui-tests.log`.
- Docs build uncovered two pre-existing broken evidence links. Both now point to
  the tracked historical GitHub artifacts; `npm --prefix docs run docs:build` passes.
- Initial tracker source research is in [tracker-sources](2026-09-30-tracker-sources.md).
  App Manager source and provenance need further comparison before integration.

## Completed repository review

The current-source review covered the following areas. Static inspection is not a
proof that every possible defect is absent.

| Area | Files and behavior reviewed | Result |
| --- | --- | --- |
| Inventory and profiles | InventoryRepository, AppInfo, PackageManagerExt, AppListViewModel, filters, selection, profile comparison | Removed a quadratic installed-package lookup; consolidated APK metadata reads and required complete base/split measurements. |
| Package operations and recovery | PackageOps, OperationJournal, SelfGrants, HistoryStore/Archive, BatchCoordinator/Store, safety/selection policies, pending recovery, undo, installer outcomes, update cleanup | Fixed live operations appearing as abandoned recovery work; kept durable recovery and mutation guards intact. |
| Reconciliation and identity | ReconcileJobService, OperationalIdentity, RecoveryProvider, backup/data extraction rules | Fixed two scheduled reconciliation jobs sharing one cancellation handle. Installation and profile identity protections remain in place. |
| Privacy and system tools | PrivacyOps/Store/Recovery/Platform, review policies, SystemOps/Policy, OTA/usage repositories, authentication | Reviewed shell boundaries, persisted targets, undo and unavailable/disconnected behavior. |
| Presets and saved state | PresetOps/Json/Store/ViewModel, SettingsStore, SavedViewStore, ManagementStore | Reviewed import/export boundaries, persistence, selection and recreation. |
| Shizuku and processes | Authorization state, binder listeners, ShellRunner, UserService, ProcessRunner, Shizuku installer bridge | Repaired authorization lifecycle, denial/retry, timeout and stale callback handling. |
| Tracker scanning | TrackerRepository, TrackerScanRepository, cache, matcher, DEX parser, APK metadata, scan UI | Bounded parsing, truthful failures, source generations, complete split checks, cancellation and cache identity. |
| UI and packaging | App list/detail, dialogs, History, Settings, navigation, strings, manifest, Gradle, CI and scripts | Added sizes and actionable progress/errors; safe external-link failures; source editor load/recreation fixes; escaped signing properties in CI. |
| Documentation and source research | Usage, release/evidence links, upstream tracker catalogs, generator and licensing | Repaired broken links and documented measurements, scanning limits and source coverage. |

## Additional fixes and regression evidence

- **Live history:** `HistoryStore.withActiveOperation` owns known running IDs. The
  banner excludes those IDs, while durable incomplete records and all recovery
  guards remain unchanged. Ownership is released even on cancellation. History
  labels live records as running. Three JVM tests cover live, interrupted and
  cancelled work; the pre-fix behavior failed two assertions.
- **DEX completeness and bounds:** an empty, malformed, truncated or oversized DEX
  fails the scan instead of yielding a false clean result. Reads are bounded to
  64 MiB per DEX, 256 MiB total and 256 DEX entries per APK, with cooperative
  cancellation. Class names stream directly into the matcher. DEX 041 containers
  follow the [Android format specification](https://source.android.com/docs/core/runtime/dex-format).
  Cana's own debug APK has a DEX over 32 MiB, so the tested limit is 64 MiB.
- **Tracker freshness:** cache schema 2 requires complete records and an APK
  fingerprint covering base/split paths, lengths and modification times. Every
  count/filter/sort/detail lookup checks version and fingerprint. Missing splits
  fail the scan, and changed APKs cannot reuse successful counts. The fingerprint
  is a freshness check, not a cryptographic content-integrity guarantee.
- **Source changes and cancellation:** generations prevent an old catalog load or
  scan from publishing after a source change. Source commits notify the application
  even if the editor closes. The source field/save action stays disabled until the
  saved value loads; the draft survives recreation. Batch summaries report failed
  scans separately, and bounded cache writes are batched.
- **Reconciliation:** each Android job ID owns its coroutine. Stopping an immediate
  job cannot cancel a delayed job. Both new regressions failed with the old single
  handle and pass after the fix.
- **Size and link UX:** update confirmation reports the known size of selected
  update APK files, shared across profiles; it no longer predicts reclaimed space.
  Unknown sizes remain unknown. External links show an error if no handler exists.
- **CI signing:** secrets are supplied through environment variables and written
  with Java-properties escaping, rather than interpolated into shell source. A
  synthetic round-trip through Java `Properties.load` covered spaces, punctuation,
  quotes, backslashes, newlines, Unicode and shell-looking text; mode is 0600. No
  real signing credentials were read or printed.
- **Tracker sources:** the [source comparison](2026-09-30-tracker-sources.md) pins
  the inspected upstream data and distinguishes confirmed signatures from ETIP
  investigations. The bundled confirmed Exodus source remains the default; no
  tentative catalog was silently added to normal counts.

An independent final review found no critical or important issue. It identified
one minor cancellation/restart race: a cancelled batch could clear its replacement's
progress. The new device regression reproduced the race before the fix and passes after it.
Progress now clears only when the finishing coroutine still owns the current batch.
The [independent review record](../evidence/2026-09-30/final-review.md) is retained.

## Final automated acceptance

- The expanded JVM suite has passed **197 tests**, with no failures or skips.
- All **27 distinct selected emulator regressions** pass, with no skips. The first
  full run passed 26/27 and exposed a test-selector mistake: a disabled text field
  has no edit action. The selector now uses its visible label; both tests in that
  class pass on rerun. Application code did not change between these runs. The
  cancellation/restart regression and source-save notification assertion passed
  in the full run. Raw results and the combined summary are retained.
- Final debug, Android-test and release APK builds pass. Both lint variants pass
  with zero errors, 493 warnings and one hint each (413 warnings concern missing
  translations). The combined lint attempt stalled in Kotlin PSI traversal; both
  variants completed in separate fresh processes. Debug test compilation and lint
  also pass after the test-selector correction.
- Release APK v2 signature verification passes. APK sizes and SHA-256 digests are
  retained in `cana-audit-apk-verification.log`.
- Documentation builds successfully. Build logs, red/green regressions, emulator
  results, signing verification and screenshots are retained under
  `docs/superpowers/evidence/2026-09-30/`.

## Original requirement traceability

| Requested outcome | Implementation / review evidence | Verification |
| --- | --- | --- |
| Comprehensive bug, omission and optimization review | Coverage table above; history ownership, reconciliation ownership, inventory lookup and CI signing fixes | Full JVM suite; final independent review; signing round-trip; both builds and lint variants |
| Repair buggy Shizuku authorization | ShizukuAuthorization, ShizukuPermission, setup dialog and saved caller state | Nine state-machine regressions, three dialog tests, MainActivity recreation regression, real denial/retry/grant and large-text acceptance |
| UI/UX sweep and improvements | Setup states and action sizes, app row/detail sizes, unknown totals, live-history presentation, tracker-source loading/draft, failed-scan counts and safe web links | Compose tests, regression suite and retained screenshots |
| APK size and system-update size in app list | AppSizeSummary, AppTile, AppInfo, ApkSizes/ApkSnapshot | Five APK size JVM tests, APK snapshot tests and two size UI tests |
| More comprehensive tracker sources | Pinned upstream/source comparison and documented decision | Primary-source research; literal/prefix comparison; no unsupported accuracy claim |
| Reliable tracker results | Complete bounded DEX reads, schema/fingerprint validation, source generations and owned scan progress | JVM DEX/cache/fingerprint tests plus repository and cancellation device regressions |

## Manual Shizuku acceptance

On the reviewed debug APK, the real shell-backed Shizuku prompt appeared. Deny kept
setup open with an explanation; retry displayed a second real prompt. Changing
font scale to 1.5 preserved setup. Granting access closed setup and opened the
requested Personal/Work profile picker. Text scale was restored to 1.0. The denial,
enlarged dialog, successful continuation and normal/enlarged app-list screenshots
are retained in the September 30 evidence folder.

This acceptance exposed the live-history warning described above, which is now
covered by regression tests. The original screenshot is retained as historical
failure evidence rather than represented as the final behavior.

The final debug APK was also inspected with shell-backed Shizuku running. Its
app-list row and Overview both showed **74.65 MB**, matching the APK file size.
Scanning that real APK completed successfully: **31,998 classes**, one APK, zero
matched tracker signatures. The list and detail screenshots and detail hierarchy
are retained as `cana-audit-final-list.png`, `cana-audit-final-detail.png` and
`cana-audit-final-detail.xml`. No recovery warning was visible in this final check.

No physical phone was modified. Tests use a disposable software emulator and
shell-backed Shizuku; OEM images, physical hardware and root-backed Shizuku remain
unverified. Static tracker matches are evidence of bundled code, not proof of
runtime behavior or absence of tracking. Native/downloaded code and network
inspection are outside this scanner's coverage.

The requested audit, fixes, research and acceptance were completed before release
packaging. On the user's subsequent instruction to commit and release, the version
advanced to 3.2.2-cana.7 / 231. The implementation evidence above precedes that
version-only change. See the [release notes](../../releases/3.2.2-cana.7.md) for
release packaging and signing continuity.
