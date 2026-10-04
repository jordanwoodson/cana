# Profile isolation and work recommendations audit

Cana now refuses operations that would remove or downgrade an app still used by another profile. This audit covers app-list and detail actions, presets, OTA reapplication, cleanup fallback, in-app Undo, pending installer recovery, and exported restore scripts.

## Findings and changes

- Ordinary uninstall no longer offers a factory reset. All ordinary removals use Android’s per-user system-app delete flag, including user apps and keep-data removal. Old reset requests are refused before dispatch; all-users and unrelated delete flags are rejected.
- Android can cascade parent removal into a child whose `deleteAppWithParent` policy is true. Cana reads each profile’s actual parent and canonical policy, checks its installed state, and blocks the parent removal before Android is called. Unknown policy fails closed for an installed child. Removing the child alone remains supported.
- Android does not expose the cascade property through the user-manager Binder API even with shell’s management permission. Cana reads the canonical per-user `dumpsys user` block. The parser ignores user-type defaults and treats absent, duplicated, or unrecognized values as unknown. Android before API 34 does not implement this property.
- Leftover update APKs are shared device-wide. Cleanup now requires the app to be uninstalled in every profile. Previous downgrade consent cannot override this rule. The operation rechecks other profiles before reset and all profiles before any fallback installation.
- Cleanup distinguishes a refusal before dispatch from a verification failure after Android changed state. A refusal cannot start fallback installation or repair. A post-reset verification error skips installation fallback and still checks whether the selected profile needs repair. Outstanding installer requests never start another mutation.
- Immediate completion verifies other profiles. Their installed, enabled, suspended and version states are also journaled before removal so a late callback or process restart cannot turn target-only verification into success. Missing peer evidence keeps the new pending record unresolved.
- In-app Undo uses the guarded removal transport. Exported restore scripts defer removal steps to Cana, record them as manual work and exit with the existing manual-work status rather than issue an unguarded shell uninstall.
- Cleanup preview cannot select a package installed in any profile, including one reinstalled in the selected profile after the request was opened.

## Work-profile guidance

Personal and other profile kinds retain the selected debloat list’s ratings. Work profiles get separate **Keep for work**, **Optional for work**, or **Review for work** explanations, with the original list rating retained in details.

Work advice combines active administrators, default roles, enabled keyboards, installed dependencies in either metadata direction, and a small explicit set of known package purposes. Infrastructure, profile managers, Google services and WebView receive conservative keep advice. Personal entertainment apps can be optional; productivity apps require checking the work workflow. Unknown packages and unavailable safety data require review. Upstream Expert/Unsafe advice is never lowered by an optional classification. No package-prefix guessing is used.

The effective work rating feeds list badges, risk filters, default actions, confirmation review and backend unsafe-removal checks. Dependency information is list-supplied, not a complete runtime dependency graph. Cana cannot infer an employer’s undocumented requirements or prove that an arbitrary package is unnecessary.

## Platform references

- [AOSP Android 16 DeletePackageHelper](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/pm/DeletePackageHelper.java): `DELETE_SYSTEM_APP`, device-wide update reset, child deletion policy.
- [AOSP Android 15 UserProperties](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/java/android/content/pm/UserProperties.java): cascade policy visibility and canonical dump.
- [AOSP Android 15 UserManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/pm/UserManagerService.java): per-user property serialization in dumpsys.
- [Android Enterprise developer guide](https://developer.android.com/work/guide): work app/data isolation and managed Google Play.
- [Android device policy controllers](https://developer.android.com/work/dpc/build-dpc): work-profile administration.

## Validation

- Required gate: `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug` passed on the final source, together with `:app:assembleDebugAndroidTest`. Toolchain: JDK 21 and SDK 36.
- **252 JVM tests passed**, none failed or skipped. Release lint has **0 errors, 754 warnings and 1 hint**; warnings include translation coverage. See [build log](gradle-checks.log).
- **11 Android 15 emulator regressions passed** on the final APK: ordinary/system uninstall in both profile directions, legacy factory-reset refusal, keeping selected-profile data, peer preservation, shared cleanup refusal even with old consent, raw reset guard, cleanup when both profiles are removed, measured freed APK bytes, work recommendation switching, and non-selectable shared cleanup UI. See [device log](device-checks.log).
- **2 additional Android 15 tests passed** on the final APK: actual Android clone-profile cascade refusal for uninstall and Undo, child-only removal, and real personal/work inventory guidance isolation. Together the final device runs cover **13 tests**. See [clone and inventory log](clone-checks.log).
- Regression-first checks observed failures for unsafe uninstall flags/global reset, unguarded exported removal, cleanup preflight fallback, delayed peer verification, and repair after a post-reset verification exception, then passed with the fixes.
- Independent code review completed with no remaining blocking findings.

Only an isolated emulator was modified. Physical/OEM devices and all API versions were not tested. Concurrent package changes by another administrator are not atomic with Cana’s checks; immediate and delayed verification detect disagreement and refuse success. Packages/data already removed by an earlier release are not recovered automatically.
