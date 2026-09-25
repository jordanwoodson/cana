# Roadmap progress and evidence

Spec: [spec.md](spec.md). Plan: [plan.md](plan.md). Base: `8e9e5b5`.

## Current state

- 2026-09-25: inspected clean repository; no roadmap features implemented at start. Created `roadmap` branch from master.
- Emulator launched as unified exec session `42132`, serial `emulator-5554`; cold boot in progress. ADB initially reports offline. No phone commands executed.
- Phase 0 code implemented in six independent commits; final app UI/integration checks in progress. Phases 1–7 and release remain pending.

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

- Phase 0: offline first-launch badges, categories/settings and profile switching; actual app reset/uninstall paths after PackageOps extraction; user-app per-profile isolation.
- All phases 1–7 acceptance gates and full release audit in plan remain open. No push/tag/release or version bump performed yet.
