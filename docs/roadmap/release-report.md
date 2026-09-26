# Cana 3.2.2-cana.3 roadmap report

Version code: 227. Application id: `io.github.jordanwoodson.cana`.

## Changes by phase

| Phase | Delivered | Main commit |
| --- | --- | --- |
| 0 | Correct verified factory reset; per-profile uninstall; main-thread switching; pure derived sorting; all five UAD categories and metadata; conditional ETag updates, timeouts, offline bundle and network preference | Separate commits below |
| 1 | Application-scoped PackageOps, bounded Shizuku UserService commands, persistent self-grants/status, durable operation journal | `1e5954c` |
| 2 | Leftover-update banner/filter/actions, cross-profile downgrade review, fallback and repair sequence, verified reclaimed APK bytes | `9fd1cd9` |
| 3 | Essential-package refusal, roles/keyboards/admins/dependents review, quoted per-profile PC recovery export | `17a0199` |
| 4 | Disable/enable, suspend/unsuspend, keep-data removal, exact undo, safer Expert/Unsafe default, tracker component inspection and supported mutation | `eede637` |
| 5 | Permission/background/metered/network privacy controls, shared-UID review, live values, desired-state persistence/reapply, privacy presets and recovery | `2e16ff7` |
| 6 | History and reverse batch undo, persistent OTA review/notifications, profile hints/all-profile presets, size/usage sorting, 90-day filter and UAD suggestions | `42dc57a` |
| 7 | Device-wide Private DNS/captive/scanning/mobile-data/Data Saver controls with verified values and exact revert | `b519cc4` |

## Upstream candidates

- `9d126ae`: reset updated system applications correctly before uninstall.
- `1bf1a79`: actually switch to the main dispatcher.
- `86ade37`: remove loading side effects from derived sorting.
- `4b11eff`: parse every UAD category and optional metadata.
- `ecc012c`: conditional list downloads, bounded I/O, offline fallback and update preferences. Omit Cana's profile-race guard when adapting upstream.
- `8c5ea38`: retry badge loading after cancellation.
- `97ccadf`: test reachability and selection of all UAD list category filters.

`d192e95` is the deliberately separate Cana-specific per-profile uninstall fix. Later feature commits depend on Cana's operation and profile model.

## Framework assumptions and evidence

All mutation evidence is from the `canta35` Android 15 emulator, personal user 0 and managed Work user 10. The physical phone was never used.

| Assumption | Observed result and implementation |
| --- | --- |
| Uninstall flags 0 reset an updated system app | Confirmed through PackageInstaller and codePath/flag readback; selected-user removal is verified separately. Both profiles tested. |
| Reset has global effects | Confirmed. Other profiles' installed state is preserved; downgrade consent is required. Report zero lasting reclamation when another profile uses the app. |
| APK sizes can be read | Confirmed for base and splits. Freed bytes count only old APK paths confirmed absent, not application data or a guessed storage delta. |
| Shizuku UserService runs as shell | Confirmed uid 2000, exit/output/timeout handling and stopped-binder errors. |
| Self-grants survive Shizuku stopping | Confirmed secure-setting write/read/revert and own-profile usage access without a running binder. |
| Shell supports disable, suspend and keep-data uninstall per profile | Confirmed, including peer-profile preservation and exact undo. |
| Shell can modify other apps' components | False for ordinary apps on Android 15. Android allows test-only packages; root-backed Shizuku can also qualify. Ordinary apps are read-only with an explanation. All four component types verified on the test-only fixture. |
| Runtime permission revoke/user-fixed is available | Confirmed through the version-aware permission adapter, including flags, immutable-policy skips and exact restoration. |
| Restricted standby always applies | False on this image: App Standby is disabled and apps remain in exempt bucket 5. Background app-op applies; standby refusal is a visible partial failure. Bucket 5 is never sent to the unsupported setter. |
| Metered UID deny persists unchanged through reboot | False on this image: Android Settings clears shell-created entries after boot. Cana saves metered intent and reconciles it alongside network intent after binder arrival, with a delayed second check. Real reboot/reapply/undo verified. |
| Connectivity package command can target a profile | False: its package-only command resolves appId and exposes no user argument. A narrow Android 14+ app_process adapter uses explicit UID rules and discovered framework methods, never guessed Binder transaction IDs. Personal/work isolation verified. |
| Desired networking survives process/reboot | Confirmed DataStore durability, binder-triggered scheduled reconciliation after an actual reboot, mismatch display, cancellation and PC recovery. Shizuku availability and Android job scheduling affect timing. |
| Cross-profile usage can be queried through shell | No supported query on this image. Own-profile usage works; usage filters/sorts are hidden elsewhere, with size/name retained. |
| OTA comparison finds returned/new packages | Confirmed using a changed stored prior fingerprint and real package inventories/removal/reinstall, including notification and consented reapply. No actual OTA image was flashed. |
| Device-wide globals can be read/set/deleted | Confirmed every supported control and exact absent/null restoration. Data Saver needs Shizuku; settings with a retained grant work without it. DNS display reports configuration, not resolver reachability. |

Detailed commands, test logs, regressions and primary framework references are in [progress.md](progress.md).

## Limits and deferred environment coverage

- Root-backed Shizuku and physical OEM devices were not exercised. Unsupported framework operations surface real errors; ordinary-app component switches remain disabled under shell on Android 15.
- Clone/private profiles use the same explicit-user paths, but only personal and managed Work profiles were available for acceptance.
- Metered/network blocks can be absent during boot until Shizuku and the scheduled reconciliation run.
- Recovery preserves recorded settings but cannot reconstruct deleted app data, removed APK versions, or an APK no longer present. Exported recovery reports manual steps and command failures.
- New strings are English pending Crowdin. Existing Hebrew plural and unused Italian format issues have narrow lint exceptions; malformed legacy percent placeholders no longer crash formatting.

## Final review and validation

One fresh reviewer inspected the complete branch. Four release blockers were reproduced and fixed:
retained-data deletion during reinstall undo, loss of recovery eligibility after an unknown
post-state, authentication bypass in new package flows, and manual-only recovery blocking older
batches. Four focused regressions failed before the fixes and passed afterward; a fifth test
verifies the new recovery state survives reopening the durable store.

The final JVM suite passes **81/81**, with debug/release lint and debug/signed release builds
passing (`/tmp/cana-final-all-checks.log`). Lint retains translation/deprecation warnings.
R8 retains both `PrivacyRecovery.main` and the Shizuku `ShellUserService` entry point.

The signed APK is version `3.2.2-cana.3`, code 227, package `io.github.jordanwoodson.cana`.
Its public signing certificate matches the previous published release:
`748e751a9dd9240f9e8cc7cd7f7218dd1541e6abaa09603d282284062639e345` (SHA-256).

Final device review acceptance **6/6 PASS**: both-profile retained-data markers, durable authentication gating, manual recovery progression, and three management UI checks. The manual recovery test needed its installed-state setup corrected before its passing rerun. The published 3.2.2-cana.2 APK upgraded in place to this exact signed APK using `adb install -r`.
Version code advanced 226→227 while preserving the first-run acknowledgement and original install
timestamp. The signed release applied/reverted Private DNS (null→opportunistic→null) and Data Saver
(disabled→enabled→disabled), exercising both native settings and its Shizuku UserService. Its
minified app_process helper successfully queried actual work-profile network rules and permission
flags. No release crash appeared in the emulator crash buffer. Test changes were restored.

APK SHA-256: `33667471af967e83980d9b734c6b06aa61eff7d6356a361a3e48be7b28950ca3`.
Evidence directory: `/tmp/cana-release-validation/` in the build workspace.

## Deferred minor

History undo and OTA reapply update their results but do not automatically refresh the main app
list. Pull to refresh after returning to Main. Mutation-time checks still use actual device state.
This does not change recorded outcomes or profile targeting.

