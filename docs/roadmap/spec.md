# Implement the Cana roadmap

You're working on **Cana**, my fork of samolego/Canta (LGPL-3.0). Cana debloats Android apps through Shizuku and adds a profile switcher for work, clone and private profiles. Implement the fixes and features below, one phase at a time.

## Context
- Repo: `/home/jordan/projects/cana/canta`, branch `master`. Remotes: `origin` = jordanwoodson/cana, `upstream` = samolego/Canta. It has a single `:app` module using Kotlin, Jetpack Compose with Material 3, proto DataStore (`app/src/main/proto`), Shizuku API 13.1.5, and `org.lsposed.hiddenapibypass` for hidden APIs.
- The Kotlin packages stay `io.github.samolego.canta` on purpose, to make upstream merges easier. The application id is `io.github.jordanwoodson.cana`.
- Toolchain: `export JAVA_HOME=~/.local/share/jdk-21 ANDROID_HOME=~/Android/Sdk`. Build with `./gradlew assembleDebug`.
- Emulator: AVD `canta35` (Android 15). There's no KVM, so start it with `-accel off`. To make it usable, run `cmd package compile -m speed -f <pkg>` on the app and `wm size 540x1200`. It's slow, so batch your checks. If Shizuku isn't on the AVD, install the latest release from RikkaApps/Shizuku and start it over adb.
- Key files:
  - `MainActivity.kt`: uninstall and reinstall through PackageInstaller and Shizuku
  - `ui/CantaApp.kt`: main screen, FAB, `uninstallOrReinstall`
  - `ui/viewmodel/AppListViewModel.kt`
  - `util/BloatUtils.kt`: the UAD list
  - `util/shizuku/ShizukuPackageInstallerUtils.kt` and `ShizukuUserUtils.kt`: per-user package and user queries
  - `util/PackageInstallerResult.kt`: waits for the real install or uninstall status
  - `extension/PackageManagerExt.kt`, `util/apps/AppInfo.kt`, `util/apps/Filter.kt`, `data/SettingsStore.kt`, `data/PresetStore.kt`, `ui/component/fab/ExpandableFAB.kt`
- There are no tests yet.

## Ground rules
- Never push, tag, create releases, run `/home/jordan/projects/cana/publish.sh`, bump the version, or touch `cana-release.jks` or `key.properties`. Never change the application id. I do releases myself.
- Work on a new branch `roadmap` off `master`, with at least one commit per phase. Keep fixes that upstream would want in their own commits, with no Cana-specific code, so I can PR them to samolego/Canta.
- Put new Cana features in new files where you can, and keep edits to upstream files small.
- Don't run anything on my real phone (motorola-edge-2025). Test on the emulator only.
- Every action must use the selected profile (`AppListViewModel.selectedUserId`). Create a managed profile on the emulator (`pm create-user --profileOf 0 --managed Work`, then `am start-user <id>`), and test both the personal and work profiles.
- For every privileged operation: log it with `LogUtils` and report real failures, following the `PackageInstallerResult` pattern (never report success that didn't happen). Show the number of failures in the UI.
- New UI strings go in `res/values/strings.xml` only. Translations come from Crowdin, so don't edit other locales.
- Several items below depend on Android framework behaviour I haven't confirmed on a device, and some shell command syntax may differ on Android 15. Check each one on the emulator before building UI on top of it. If something behaves differently, adapt and tell me.
- Match the existing code style. Add JVM unit tests in `app/src/test` for pure logic: UAD parsing, update-check decisions, restore-script generation, safety-check decisions and uid math.
- Only stop to ask when you're blocked on a decision that's mine to make. Otherwise keep going through all phases.

## Phase 0: Fixes (upstreamable, separate commits)
1. **Reset to factory never resets.** In `MainActivity.uninstallApp` (around lines 110–145), the "reset" call and the real uninstall both use `DELETE_SYSTEM_APP` (0x4). That flag only marks the app uninstalled for the user and leaves the update in `/data/app`.
   - Fix: first call `uninstall` with flags `0`, which downgrades the updated system app for all users. Check that `FLAG_UPDATED_SYSTEM_APP` is gone, then uninstall with `DELETE_SYSTEM_APP`.
   - Count it as success only if both steps' `PackageInstallerResult` succeed.
   - The downgrade affects every profile. Phase 2 adds the warning for that.
2. **Drop `DELETE_ALL_USERS`.** Uninstalling a user app from the current profile uses `DELETE_ALL_USERS` (0x2), which also removes it from the work profile. For every profile, use `DELETE_SYSTEM_APP` for system apps and `0` otherwise; Android deletes the app completely once no user has it. This one is Cana-specific, so give it its own commit.
3. **`with(Dispatchers.Main)` in `CantaApp.kt` `uninstallOrReinstall`** (around lines 546 and 559) is Kotlin's `with` scope function, not a thread switch. Use `withContext(Dispatchers.Main)`.
4. **`AppListViewModel.sortedList`** (around line 74) sets `isLoading` inside `derivedStateOf`. Remove that side effect.
5. **UAD list parsing** (`BloatUtils.kt`): `InstallData` only knows OEM and CARRIER, so the Google, Aosp and Misc entries (about 888 of 5,381) lose their category.
   - Support every list value, mapping unknown values to null.
   - Parse `dependencies`, `neededBy`, `labels` and `suggestions`.
   - Add list-category filters for Google, OEM, Carrier, AOSP and Misc.
6. **UAD list download** (`BloatUtils.kt`, `AppListViewModel.loadInstalled`): `URL.readText()` has no timeouts. Every launch also calls the GitHub commits API (60 requests an hour without login) before downloading the 1.6 MB list.
   - Replace this with one conditional GET on the list URL using `ETag` and `If-None-Match`. raw.githubusercontent.com returns an ETag; store it in a new settings proto field.
   - Add connect and read timeouts.
   - Check at most once every 24 hours, unless the user pulls to refresh.
   - Add an "Update list only on unmetered networks" setting.
   - Bundle a copy of the list in `assets/` as the fallback for offline use and the first run.
   - Keep the custom list URL setting working. Leave the old `commits_url` proto field in place for compatibility, but stop using it.

## Phase 1: Foundation
1. **`PackageOps`**: move uninstall, reinstall and reset out of `MainActivity` into one class that uses the application context and knows about userId. The view models call it, so `CantaApp` doesn't need more lambdas passed in. Every operation returns a result with a message.
2. **Shell runner**: a Shizuku UserService (AIDL, running as the Shizuku uid) that exposes `exec(argv)` and returns `{exitCode, stdout, stderr}`, with a timeout. Use it for the `cmd`, `settings`, `appops` and `am` features below, instead of calling more hidden AIDLs through reflection. Handle Shizuku not running.
3. **Self-grants**: grant these once through Shizuku, and show their status in settings. Declare `WRITE_SECURE_SETTINGS` and grant it with `pm grant <our id> android.permission.WRITE_SECURE_SETTINGS`. Declare `PACKAGE_USAGE_STATS` and allow it with `appops set <our id> GET_USAGE_STATS allow`. The features that use them then work even when Shizuku isn't running.
4. **Operation history store** (proto DataStore): timestamp, userId, package, action, previous state and result. Every later phase writes to it.

## Phase 2: Remove leftover system updates
- **New `AppInfo` fields:** `isUpdatedSystemApp` and `updateSizeBytes`. When `sourceDir` is under `/data/app`, the size is `File(sourceDir).length()` plus the `splitSourceDirs`; other apps' APKs are world-readable. Use the info that `MATCH_UNINSTALLED_PACKAGES` already returns, including for other profiles.
- **Uninstalled tab:**
  - Replace the Reinstall FAB with `ExpandableFAB`, offering Reinstall and "Remove updates".
  - Add a banner: "N apps · X MB of updates still on disk — Remove".
  - Add a "Leftover updates" filter and a button in `AppInfoDialog`.
- **For each app:**
  1. Skip it unless `FLAG_UPDATED_SYSTEM_APP` is set.
  2. Check every profile, using `ShizukuUserUtils.getUsers()` and a per-user `getPackageInfo`. If the app is still installed in another profile, warn: removing the update downgrades it there too, Play will update it again, and no space is saved. Let the user skip it.
  3. Try `uninstall(pkg, flags = 0)` for the selected user.
  4. Check two things: the update flag is gone, and the app is still uninstalled for the user. If it came back as installed, uninstall it again with `DELETE_SYSTEM_APP`.
  5. If the direct call fails, fall back to install-existing → uninstall(0) → uninstall(`DELETE_SYSTEM_APP`).
  6. Report the space freed.
- **Installed-tab uninstall dialog:** show how much space removing updates would free. Tick "reset to factory" by default when the app isn't installed in another profile.
- **How to test on the emulator:**
  1. Pick a harmless system app and make it "updated" by reinstalling its own APK: find it with `pm path <pkg>`, copy it to `/data/local/tmp`, then `pm install -r`.
  2. Confirm with `dumpsys package <pkg>`: codePath is under `/data/app` and `FLAG_UPDATED_SYSTEM_APP` is set.
  3. Uninstall the app for user 0, then run Remove updates.
  4. Confirm that codePath is back on `/system` and that each user's installed state hasn't changed.

## Phase 3: Safety
- **Always-protected packages** (refuse, with no override): Cana, Shizuku (`moe.shizuku.privileged.api`), `com.android.shell` (removing it breaks Shizuku), `android`, `com.android.systemui`, `com.android.settings`, the package installer and the permission controller.
- **Warn and require explicit confirmation** before removing or disabling:
  - Current role holders: HOME, SMS, DIALER, BROWSER and ASSISTANT, via `cmd role get-role-holders --user N <role>`
  - The current and enabled keyboards: `settings --user N get secure default_input_method` and `enabled_input_methods`
  - Active device admins
  - A package whose UAD `neededBy` list includes an app that's still installed
- **Restore script export**: generate a `.sh` file (via `ACTION_CREATE_DOCUMENT`) that undoes everything in the history, per profile: `cmd package install-existing --user N`, `pm enable`, `pm unsuspend`, resets for appops, netpolicy and standby, and component re-enables. Then I can recover from a PC over adb.

## Phase 4: More debloat actions
- **New actions next to Uninstall, each with its undo:**
  - **Disable** (`pm disable-user --user N` or `setApplicationEnabledSetting` with DISABLED_USER)
  - **Suspend** (`pm suspend --user N`)
  - **Uninstall keeping data** (`DELETE_KEEP_DATA`, 0x1)

  Make Disable the default for UAD Expert and Unsafe entries.
- **Component blocking**:
  - List an app's services, receivers, providers and activities.
  - Flag the ones that match tracker class prefixes from Exodus Privacy's signatures. Check their data licence first. If it doesn't allow bundling, ship a small curated list with attribution and support a custom list URL.
  - Let me disable and re-enable components per profile.
  - Check that the shell uid can change component state for other apps on Android 15.

## Phase 5: Locking down apps I keep (privacy and data saving)
For each app and profile, show the current state and offer a one-tap revert:
- **Permissions:** revoke runtime permissions in bulk and mark them user-fixed. Use `pm revoke` plus `pm set-permission-flags`, or the permission manager binder; check which exists on Android 15.
- **Background:** `cmd appops set --user N <pkg> RUN_ANY_IN_BACKGROUND ignore` and `am set-standby-bucket --user N <pkg> restricted`.
- **Data:** deny background data on metered networks with `cmd netpolicy add restrict-background-blacklist <uid>`. Here uid = userId × 100000 + appId, and the setting persists across reboots.
- **Network block (Android 14+):** `cmd connectivity set-chain3-enabled true` and then `set-package-networking-enabled false <pkg>`. This resets on reboot, so:
  - Keep the desired state in a store.
  - Reapply it on `BOOT_COMPLETED` once Shizuku's binder arrives (`Shizuku.addBinderReceivedListener`).
  - Show where the actual state differs from the desired state.
- Presets should be able to include these lockdown settings, not just removals.

## Phase 6: Management
- **History page with "Undo last batch"**, using the Phase 1 store.
- **OTA detection:**
  - Store `Build.FINGERPRINT` and the known package set for each profile.
  - When the fingerprint changes (checked at app start, `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`), find apps Cana removed that came back, plus any new system packages.
  - Post a notification and show a banner offering to reapply. This needs `RECEIVE_BOOT_COMPLETED` and `POST_NOTIFICATIONS`.
- **Presets:** add an optional profile kind (a new proto field; keep JSON import and export backward-compatible), plus "apply to all profiles".
- **Sorting and filters:**
  - Sort by size and by last used. Last-used data comes from UsageStats via the self-granted permission. For other profiles, use the shell runner if that works; otherwise hide the column.
  - Add an "Unused for 90 days" filter.
  - Show the UAD `suggestions` category (for example "alternatives: cameras") in `AppInfoDialog`.

## Phase 7: Device-wide toggles (a separate "System" screen)
Each toggle shows its current value and can revert to it:
- Private DNS (`private_dns_mode` and `private_dns_specifier`), with a custom hostname and a few presets
- The captive portal check: default, an alternative endpoint such as GrapheneOS's (check the current URLs), or off with a warning
- `wifi_scan_always_enabled`, `ble_scan_always_enabled` and `mobile_data_always_on`
- The global Data Saver (`cmd netpolicy set restrict-background true`)

## When you're done
Report:
- What each phase added
- Which commits could go upstream
- Every assumption about framework behaviour, and whether you confirmed it on the emulator
- Anything you skipped, and why
- Draft release notes

Leave the branch unpushed.
