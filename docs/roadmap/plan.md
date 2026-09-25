# Cana roadmap implementation plan

> Execute with superpowers:executing-plans, task by task. The user's instruction to continue through all phases authorizes implementation without intermediate approval gates.

**Goal:** Implement every requirement in [spec.md](spec.md), verify it on Android 15 personal and work profiles, and publish a signed GitHub release usable by Obtainium.

**Architecture:** Keep upstream fixes in independent commits. Introduce application-scoped PackageOps, a bounded Shizuku UserService shell transport, and proto history/desired-state stores. Compose view models orchestrate selected-profile batches; every privileged result is verified, logged, recorded, and counted. Pure policies and script generation have JVM tests; Android privileges and actual state changes have emulator evidence.

**Tech stack:** Kotlin, Compose Material 3, proto DataStore, Shizuku 13.1.5, Android 15 emulator, JUnit.

## Constraints and rulings

- Branch `roadmap` starts at `8e9e5b5` on `master`; at least one commit per phase, upstreamable fixes separate from Cana changes.
- Keep application id `io.github.jordanwoodson.cana` and Kotlin namespace `io.github.samolego.canta`.
- Only emulator `canta35`; always explicitly pass emulator serial to adb. Never operate on the real phone.
- Every per-app operation captures `selectedUserId`. Device-wide changes must say so and preserve prior state.
- New strings only in `res/values/strings.xml`; do not edit translations or signing credentials.
- Ruling: latest user goal explicitly requests push and Obtainium availability, overriding the original spec's no-push/no-release/no-version-bump instructions. Publishing is the final step after validation; existing signing configuration may be used without modifying credentials.
- Ruling: implement inline on the requested new branch in this clean checkout. No separate worktree is needed. Keep this durable plan and evidence ledger throughout the goal.

## Review focus

- Mid-batch profile changes and Shizuku death: no operation or UI update migrates to a different user; preserve partial failures.
- Multi-step actions that partly succeed: record actual previous/result state; never count an unverified reset or restore as success.
- Offline/corrupt/custom UAD data: usable bundled fallback, no cache poisoning or stale ETag across URLs, no repeated launch downloads.
- Recovery across process death/reboot/OTA: durable history and desired state, idempotent restore and notification behavior.
- OEM/Android command restrictions: probe before UI, verify exit codes plus actual state, show unsupported actions honestly.

## Tasks and acceptance gates

### Task 1: Phase 0 correctness fixes

Files: `MainActivity.kt`, `util/UninstallSequence.kt`, `ui/CantaApp.kt`, `ui/viewmodel/AppListViewModel.kt`, corresponding JVM tests.

- [x] Test reset(0) then verify no update flag then per-user uninstall; failed reset/check/final uninstall must fail. Implement a pure sequence using callbacks to the real PackageInstaller result adapter.
- [x] Commit reset fix separately as upstreamable.
- [x] Remove DELETE_ALL_USERS for user apps, test profile flag decisions and emulator isolation, commit separately as Cana-specific.
- [x] Replace `with(Dispatchers.Main)` with `withContext`; remove derived-state loading side effects; separate upstreamable commits.
- [x] Run `./gradlew testDebugUnitTest assembleDebug`; expected PASS. Exercise reset and profile uninstall on emulator, preserving other profile's installed state.

### Task 2: Phase 0 UAD data and updates

Files: `util/BloatUtils.kt`, `util/BloatListRepository.kt`, `util/BloatUpdatePolicy.kt`, settings proto/store/view model/screen, app-list loading/filter UI, `assets/uad_lists.json`, tests.

- [x] Test all categories, unknown/missing values, dependencies/neededBy/labels/suggestions with real JSON. Implement parser and category filters; commit upstreamable.
- [x] Test first load, 24h boundary, manual refresh, auto-update off, metered restriction, clock rollback, URL changes, 200/304/error/corrupt responses and fallback. Implement one conditional GET with timeouts, atomic validated cache, URL-scoped ETag and last attempt time; retain deprecated proto fields.
- [x] Bundle attributed UAD snapshot; preserve custom list URL; add unmetered setting and force refresh wiring. Commit upstreamable.
- [x] Run JVM suite and debug build; verify offline first launch and settings/filter/refresh UI on emulator.

### Task 3: Phase 1 foundation

Files: new `ops/PackageOps.kt`, `ops/OperationResult.kt`, `ops/ShellRunner.kt`, `service/ShellUserService.kt`, AIDL interface/result, `data/HistoryStore.kt`, history proto, grants settings UI.

- [x] Extract PackageOps(applicationContext) with userId and message-bearing results; view models call it directly. Remove activity operation lambdas.
- [x] Add bounded argv-only shell exec with timeout, stdout/stderr/exit code, disconnect/not-running results, service lifecycle handling. Probe from Shizuku uid.
- [x] Declare and self-grant WRITE_SECURE_SETTINGS / PACKAGE_USAGE_STATS once, verify and display live status.
- [x] Persist timestamp, batch id, userId, package, action, previous state and real result for all operations, including failures. Tests cover output limits, timeout, sequencing and store roundtrip.
- [x] Build/test and emulator Shizuku running/stopped checks, commit phase.

### Task 4: Phase 2 leftover updates

Files: `AppInfo.kt`, new update cleanup coordinator/policy, app list/banner/filter/FAB/info and uninstall dialogs.

- [x] Add updated-system flag and base+split APK sizes. Query uninstalled packages for each user.
- [x] Implement profile warning/skip, direct reset, verified selected-user uninstalled state, repair if resurrected, and install-existing fallback. Record every step and actual freed bytes.
- [x] Add uninstalled ExpandableFAB, banner, filter, app-info button; installed reset defaults only when no other profile uses package, with global warning and size.
- [x] Test decisions and failure propagation. On emulator update a harmless system APK, uninstall for selected user, clean and verify system codePath plus unchanged user install states. Commit phase.

### Task 5: Phase 3 safety and recovery

Files: new safety policy/probe/dialog, restore-script generator, history export entry point.

- [x] Always deny Cana/Shizuku/shell/framework/SystemUI/settings/installers/permission controllers, including resolved OEM handlers.
- [x] Probe selected-user roles, current/enabled IMEs, admins and installed UAD dependents; require explicit confirmation for remove/disable. Fail closed when a safety query fails.
- [x] ACTION_CREATE_DOCUMENT exports shell-quoted reverse chronological per-user undo script for all supported history actions. Test protected packages, warnings, partial successes, uid math, escaping and previous-state restoration.
- [x] Emulator verifies protected refusal and warnings, export, and script recovery. Commit phase.

### Task 6: Phase 4 debloat actions and components

Files: PackageOps/action UI, new component repository/dialog, tracker signature source/assets attribution.

- [ ] Probe disable/enable, suspend/unsuspend, keep-data uninstall and cross-app component mutation on Android 15.
- [ ] Implement actions and undo, default Disable for Expert/Unsafe. Route destructive actions through safety and history.
- [ ] Enumerate services/receivers/providers/activities and per-user state; identify trackers using licensed Exodus data or attributed curated fallback plus custom URL.
- [ ] Verify all actions/undo on personal and work profiles; tests for matching/commands/results. Commit phase.

### Task 7: Phase 5 lockdown

Files: new privacy repository/panel, desired-state proto/store, boot/binder receiver, preset proto extensions.

- [ ] Probe Android 15 permission revoke + user-fixed, appops/background standby, uid netpolicy and connectivity chain support before UI.
- [ ] Show actual per-profile permission/background/data/network state and one-tap restoration of captured prior values.
- [ ] Persist desired network blocks, reapply after boot when Shizuku binder arrives, show desired/actual mismatch. Do not fake support on incompatible devices.
- [ ] Extend presets with lockdown actions; test uid = userId*100000+appId and restore policies. Emulator verifies selected-profile effects, revert, reboot and binder timing. Commit phase.

### Task 8: Phase 6 management

Files: new history page/undo coordinator, OTA detector/store/notification receiver, preset profile kinds, sort/usage UI.

- [ ] History page and Undo last batch reverse verified changes, report partial failures and avoid recursive undo selection.
- [ ] Store fingerprint and package sets by profile. At startup/boot/package replacement detect removed apps restored and new system apps; notification/banner reapply passes safety. Declare/request notifications.
- [ ] Backward-compatible optional profile kind in proto/JSON and apply-to-all with individual user results.
- [ ] Size/last-used sort, unused-90-days filter, UsageStats self grant; probe cross-profile availability and hide unavailable data. Show UAD suggestions.
- [ ] JVM tests for OTA decisions, old/new presets and undo; emulator UI/action checks. Commit phase.

### Task 9: Phase 7 system controls

Files: new System screen/view model/repository and navigation, system-action history types.

- [ ] Probe private DNS, captive portal endpoint/default/off, scanning/mobile data and global Data Saver; verify current GrapheneOS URLs from primary sources.
- [ ] Show live values, custom DNS/presets, warnings for off, and revert to captured previous values. Use self-granted API when available; shell when needed.
- [ ] Test commands/validation/restore; emulator change/readback/revert for each toggle. Commit phase.

### Task 10: audit and release

- [ ] Fresh review of the whole branch, resolve significant findings with regressions; verify every spec item using current evidence in `progress.md`.
- [ ] Run complete JVM tests, lint, debug and signed release builds. Verify release package id/version/signature and install upgrade on emulator.
- [ ] Commit final version/release notes and phase report listing upstream commits, confirmed assumptions, adaptations and any gaps. Completion requires no unexplained gaps.
- [ ] Push final commit/branch, publish non-prerelease GitHub tag matching versionName with signed APK and checksums. Verify downloadable release metadata is compatible with existing Obtainium config.
