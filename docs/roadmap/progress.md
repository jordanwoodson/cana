# Roadmap progress and evidence

Spec: [spec.md](spec.md). Plan: [plan.md](plan.md). Base: `8e9e5b5`.

## Current state

- 2026-09-25: inspected clean repository; no roadmap features implemented at start. Created `roadmap` branch from master.
- Emulator launched as unified exec session `42132`, serial `emulator-5554`; cold boot in progress. ADB initially reports offline. No phone commands executed.
- Phase 0 implemented in six independent commits. Phase 1 implementation and emulator checks pass. Phases 2–3 implementation and emulator verification pass. Phases 4–7 and release remain pending.

## Interface preflight

- Phase 0 sequence/result must be reusable by Phase 1 PackageOps and Phase 2 update cleanup; retain real PackageInstaller result handling.
- Phase 0 parser metadata feeds safety dependents and management suggestions; unknown values remain nullable and collection fields default empty.
- Phase 1 history supplies phases 3–7 undo/export; store batch identity and previous-state payload, preserving partial changes.
- Phase 3 safety applies at the operation boundary to Phase 4 actions, preset batches and OTA reapply, not solely in dialogs.
- Phase 5 desired networking state differs from history and actual state; boot and binder arrival both trigger reconciliation.
- Phase 6 presets must decode existing JSON/proto defaults; profile kind remains optional.

## Decisions

- Latest user release instruction supersedes original leave-unpushed rule; credentials stay unchanged.
- User explicitly requested continuing all phases without avoidable questions; execute inline with durable evidence instead of repeated design approvals.

## Verification

### Phase 0

- `9d126ae`: reset flags 0, verify update flag removed, then per-user uninstall. Six JVM sequence tests; four reproduced old failures before passing. Candidate upstream fix.
- `d192e95`: remove DELETE_ALL_USERS; Cana-specific profile-isolation commit.
- `1bf1a79`: actual main dispatcher switch. Candidate upstream fix.
- `86ade37`: side-effect-free derived sorting. Candidate upstream fix.
- `4b11eff`: all five UAD categories, null/unknown-safe metadata, relations and suggestions, category filters. Four regressions reproduced before passing. Candidate upstream fix.
- `ecc012c`: conditional GET, ETag/cache scoped to custom URL, bounded timeouts, 24h failed/successful attempt throttling, manual refresh, unmetered preference, atomic validated cache and attributed bundled snapshot. Candidate upstream feature; package-profile race guard can be omitted when adapting upstream.
- JVM suite: **20 tests, zero failures**, `testDebugUnitTest`; debug APK builds successfully. Logs `/tmp/cana-phase0-validation.log`; XML reports under `app/build/test-results/testDebugUnitTest/`.
- `lintDebug` currently fails: **31 errors, 208 warnings**, including existing resource-format issues and Crowdin missing translations (seven new untranslated Phase 0 strings). Do not claim clean lint. Resolve actual formatting defects and establish deliberate Crowdin lint policy before release; never modify other locales.
- Bundled UAD data includes duplicate JSON keys. JVM JSON.org parser rejected these; Android's parser uses last-key-wins. Switched test-only library to Android JSON implementation and parsed the entire unchanged snapshot successfully.
- Emulator Android 15 fingerprint `Android/sdk_phone64_x86_64/emu64x:15/AE3A.240806.019/12368160:userdebug/test-keys`. Existing managed Work profile id 10 started; personal id 0.
- Staged Print Spooler update from its own system APK: codePath `/data/app/...`, UPDATED_SYSTEM_APP set, users 0/10 installed. `pm uninstall --user 0 com.android.printspooler` left update on disk and user 10 installed. `cmd package uninstall-system-updates com.android.printspooler` returned Success, codePath `/system/app/PrintSpooler`, flag cleared, user 0 still uninstalled and user 10 still installed. Evidence `/tmp/cana-reset-probe-{before,uninstalled,reset}.log`. This confirms framework behavior but does not substitute for testing the app's PackageInstaller calls.
- Shizuku started on emulator via its bundled `libshizuku.so`; `ps` verifies `shizuku_server` runs as shell uid. Debug Cana installed after replacing old release-signed emulator installation. Offline launch initiated; Quickstep launcher ANR obstructed first screenshot, retry in progress. Phone untouched.

### Remaining verification

- Phase 0: offline first launch, category/menu interaction, persisted update settings, actual reset and both-profile isolation verified.
- Phases 0–3 implementation and listed acceptance checks pass; Phases 4–7 and full release audit in plan remain open. No push/tag/release or version bump performed yet.

### Phase 1

- Implemented application-context PackageOps, explicit-user uninstall/reinstall/reset sequence, verified final installed state, per-operation and batch results. Removed all operation lambdas from MainActivity/CantaApp. View model captures selected user/packages once, prevents overlapping batches, and avoids applying old-user results to a newly selected profile.
- Implemented AIDL Shizuku UserService with direct argv execution, platform-command allowlist, 1–30s command timeout, separate stdout/stderr, 64 KiB per-stream capture, and explicit error on truncation. Service connection timeout and unavailable/denied/dead binder failures return errors. Service terminates with its client, using Shizuku's reserved destroy transaction.
- Added self-grants for WRITE_SECURE_SETTINGS and usage app-op; auto-grant only missing grants when an authorized binder arrives. Live status and retry action in settings. Grants use Cana's own installation user, not the app-management profile, because they grant Cana's process permissions.
- Added proto operation history with id, batch id, timestamp, user, package, action, previous/after state, pending/completed, success/message, changed and future undo link. Journal intent before mutation; finish verification/journaling even if caller leaves. History write failure refuses an unrecorded operation; partial failure remains recorded.
- JVM suite: **27 tests, zero failures**; includes five process-runner tests (all RED before implementation) and two DataStore tests (both RED before implementation). History roundtrip verifies pending records and partially applied failures survive reopening.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest`: success (`/tmp/cana-phase1-final-build.log`). Lint issues recorded above remain unresolved.
- Initial device suite correctly refused to run without Shizuku authorization. `pm grant API_V23` alone does not update modern Shizuku's internal authorization; authorized through the normal emulator dialog, then reran.
- `FoundationDeviceTest`: **5/5 PASS** (`/tmp/cana-phase1-device-tests-authorized.log`, 42.194s). Proves shell uid 2000, actual command failure propagation, self-grants and no repeated grant operations, real PackageInstaller factory reset followed by per-user removal, ordinary-user-app personal/work isolation in both directions, real reinstall, failure count and persisted history.
- `UnavailableServiceTest`: **1/1 PASS** (`/tmp/cana-phase1-shizuku-stopped.log`, 0.807s) after stopping emulator Shizuku. Shell action fails; grants persist; secure setting write/read/restore and UsageStats query work without Shizuku.
- Test fixture: code-free `io.github.jordanwoodson.cana.fixture`, generated by `scripts/create-test-fixture.sh` using the standard debug key. Only emulator installed. System fixture `com.android.printspooler` restored installed for users 0 and 10 after tests. All instrumentation asserts emulator hardware before mutating anything.
- Emulator launcher/System UI ANRs during cold boot were external to Cana; disabled animations and Bluetooth, compiled System UI/app/test APKs, and recovered. Offline screenshot subsequently showed the app and all recommendation badges correctly. No Cana crash observed.

- Settings UI visually verified (`/tmp/cana-phase1-settings.png`): secure settings and usage access both Allowed, list switches visible. Toggled auto-update/unmetered preferences; inspecting persisted proto. A navigation-during-badge-loading edge case was observed: cached app rows can return without badges after their loader is cancelled. Fix and regression required before proceeding to Phase 2.

- Expanded actual factory-reset test now passes for both user 0 and user 10, verifying the other profile remains installed each time: `/tmp/cana-phase1-reset-both-profiles.log`, 1/1 PASS, 32.747s.
- Decoded emulator settings proto confirms auto-update false (default omitted) and `bloat_unmetered_only: true` after UI toggles; preferences persist.

- `8c5ea38` fixes interrupted badge loading: dedicated emulator test reproduced stale metadata after cancellation (RED), then passed after explicit load-completeness tracking (GREEN, 5.021s). Upstream candidate.
- Category menu Compose test: all five categories visible through scrolling, AOSP selectable and reflected in menu, 1/1 PASS (`/tmp/cana-phase0-category-ui.log`, 22.406s).
- Phase 1 implementation commit: `1e5954c`. JVM suite remains 27/27, latest debug + instrumentation builds pass.

### Phase 2

- Added updated-system state and unique base/split APK byte accounting from MATCH_UNINSTALLED_PACKAGES, including other profiles. The uninstalled list has a leftover banner/filter, labeled expandable Reinstall/Remove updates actions, and an app-info cleanup button.
- Cleanup checks every profile, defaults shared apps to skipped, requires explicit downgrade consent and rechecks at mutation time. Direct reset verifies both update removal and selected-user absence; fallback install/reset/remove also repairs the original state after a failed reset. All profile installed states are checked after cleanup.
- Installed-app confirmation estimates reclaimable APK bytes, explains device-wide reset, and defaults reset on only if every updated selection is unused by other profiles. Requests capture the profile/packages before confirmation or authorization. Package mutations are serialized; history now stores actual freed bytes.
- Ruling: report zero lasting reclamation when another profile uses the app, as requested by the spec. Otherwise count only the old update APK paths confirmed absent after cleanup; do not count application data or claim a free-storage delta.
- Ten new JVM tests: seven assertions failed against stubs before implementation; **37/37 total pass**. Covers direct/fallback/repair failures, missing verification, exception recovery, skip, and unique base/split sizing. Logs `/tmp/cana-phase2-sequence-{red,green}.log`, `/tmp/cana-phase2-ui-build.log`.
- Actual direct PackageInstaller reset on an already-uninstalled updated package: **1/1 PASS**, both profile directions, 32.437s (`/tmp/cana-phase2-direct-probe.log`). Android 15 supports direct removal; fallback is verified with deterministic failure tests.
- Full cleanup integration: **1/1 PASS**, 48.993s (`/tmp/cana-phase2-cleanup-device.log`). Verifies shared-consent refusal, unchanged state on refusal, successful cleanup in both directions, AppInfo flags and readable APK size, durable history, no-update skip, unchanged installation state for both profiles, and exact freed bytes when both profiles are uninstalled. Print Spooler restored afterward.
- Compose confirmation/actions: **2/2 PASS**, 65.543s (`/tmp/cana-phase2-ui.log`): both labeled actions work; shared work-profile cleanup starts unchecked, cannot confirm before selection, shows the downgrade warning and captured profile, then emits the exact approved peer ids. A compile-only import cleanup mistake was corrected before the successful build/test; an earlier stale-test-APK run was discarded.

### Phase 3

- Phase 2 commit: `9fd1cd9` (Cana-specific profile-aware cleanup).
- Added immutable safety policy plus live inspection at the PackageOps mutation boundary. Core packages cannot be overridden; installer/permission/settings handlers are also resolved per user, with the actual platform permission-controller package protected. Roles, current/enabled IMEs, active admins and installed UAD dependents require fresh explicit warning consent. Query errors refuse the mutation. Android 9 uses default-intent/assistant queries because RoleManager arrived in Android 10.
- UI shows essential-package refusals and a separate acknowledgement for actionable warnings, including when ordinary uninstall confirmation is disabled. Protected entries are skipped in mixed selections; backend refusal remains mandatory.
- Recovery export uses ACTION_CREATE_DOCUMENT from Logs. Script arguments are shell-quoted and undo commands run in reverse history order, retaining partial/pending records and continuing after command errors. Restores captured enabled/component/suspend/permission flags/app-op/standby/netpolicy/global values; uid math includes the recorded profile. Non-recoverable update APKs, deleted app data and missing/unknown old state are explicit manual steps, never claimed as restored. Future phases populate the corresponding state fields/actions and add in-app undo.
- Package history now captures exact enabled state, system status and suspension; new self-grants preserve original usage app-op mode. Older history without that mode yields a manual recovery note rather than an invented prior value.
- JVM **47/47 PASS** (`/tmp/cana-phase3-unit-green.log`, `/tmp/cana-phase3-full-build.log`). Four safety and six restore regressions each failed before implementation. Shell-quoting test executes actual local `sh` and verifies substitution/quotes/newlines remain literal.
- Safety device test **1/1 PASS**, 28.539s (`/tmp/cana-phase3-safety-device.log`): both-profile handler/role/IME/admin/installed queries succeed, fixture is allowed, package installer is protected, keyboard removal without warning consent is refused without change, essential package refusal cannot be overridden.
- Warning UI **1/1 PASS** within `/tmp/cana-phase3-recovery-ui.log`: an enabled keyboard remains blocked until the separate warning acknowledgement even with ordinary confirmation disabled.
- Recovery script emulator integration **1/1 PASS**, 21.942s (`/tmp/cana-phase3-recovery-green.log`): actual work-profile removal then generated script restores install and original enabled state, preserving personal installation. Initial harness passed shell quotes to UiAutomation (which tokenizes directly), yielding no output; corrected only the harness, then verified real execution. `sh -n` also passed on Android.
- Actual document-picker export saved `/sdcard/Download/cana-restore.sh`, 5,920 bytes. Pulled `/tmp/cana-exported-restore.sh` passes shell syntax check. UI evidence `/tmp/cana-phase3-export-logs.png` and `/tmp/cana-phase3-export-saved.png`.
- Primary API references: [AOSP roles](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/main/PermissionController/src/com/android/permissioncontroller/role/Role.md), [IDevicePolicyManager](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/admin/IDevicePolicyManager.aidl). Shell installer resolution needs an APK content URI; querying only action and MIME returns no handlers on this emulator.

### Phase 4 preflight

- Exodus README/API terms permit database redistribution under ODbL 1.0, contents under DbCL 1.0. Public `/api/trackers` download succeeded once to `/tmp/cana-exodus-trackers.json`: 432 trackers, 571,866 bytes. Bundle the unchanged snapshot with source/hash/date and license links; show attribution by component matches. Do not call the Exodus API automatically from end-user apps (their API docs discourage production clients). Custom URL support remains required.
- Primary terms: https://raw.githubusercontent.com/Exodus-Privacy/exodus/v1/README.md and https://raw.githubusercontent.com/Exodus-Privacy/exodus/v1/doc/api.md; ODbL https://opendatacommons.org/licenses/odbl/1-0/ sections 4.2–4.6. Database JSON is separate from Cana source licensing.
- Later Phase 5 preflight: `/tmp/cana-connectivity-help.txt` confirms OEM_DENY_3 set/get commands exist on this Android 15 emulator, but package commands expose no `--user`. Determine actual uid resolution before enabling work-profile network controls. Current recovery planner refuses nonzero-user network records until a verified interface exists; do not silently act on user 0.
