# Read-only profile comparison (Task 6 / F3)

Implemented in new files only. No navigation, CanaServices, inventory repository or existing app files were modified by this worker for this task.

## Delivered behavior

- `ProfileComparisonPage(onNavigateBack: () -> Unit)` reads profiles through `ShizukuUserUtils.getUsers()` and package snapshots through `CanaServices.inventory.snapshot(userId)`. It never invokes a package/privacy mutation.
- Users choose profile columns, search by package name or labels from any selected profile, and refresh/retry. Selection and search survive ordinary saved-state restoration. The selected profiles are identified by name and Android user ID.
- The matrix joins by package name and preserves each profile identity. It distinguishes installed, removed, not present and unavailable. Disabled and suspended are independent flags, both visible when applicable. Removed apps do not retain stale enabled/suspended flags.
- An inventory is accepted only when `loaded && !stale && error == null`. Inaccessible or incomplete inventory is represented as null, never as an empty successful result. A failed profile-list lookup keeps previously known profiles, or shows the current installation profile with a visible profile-list warning when no prior list exists.
- Saved network and metered intentions come from `desiredPrivacy.blocks` and `meteredBlocks`. They remain visibly labeled as **saved** choices, including for a package whose profile cannot be queried. An unavailable privacy store is distinct from no saved restrictions. Saved settings in another user are never assigned to this user.
- The page explicitly says that saved privacy choices do not verify live enforcement, and that runtime permissions/background activity are outside this comparison. It does not label those unknown dimensions as unrestricted.
- Clear-search, no-selection, no-apps, no-matches, unavailable-inventory, loading and retry states are distinct. Raw errors are in expandable technical details. Controls occupy a bounded scrolling region; the matrix has horizontal scrolling and virtualized vertical rows, with a fixed matrix header during vertical scrolling. Accessibility cells include package and profile identity.

## New files / integration

- `app/src/main/java/io/github/samolego/canta/ops/ProfileComparison.kt`
- `app/src/test/java/io/github/samolego/canta/ops/ProfileComparisonTest.kt`
- `app/src/main/java/io/github/samolego/canta/ui/screen/ProfileComparisonPage.kt`
- `app/src/main/res/values/strings_comparison.xml`

Parent has integrated the comparison route/menu. Screen entry point:

```kotlin
ProfileComparisonPage(onNavigateBack = { /* existing navigation pop */ })
```

The pure policy is `ProfileComparison.rows(List<ComparisonProfileInventory>, query: String = "")`. Inputs use `packages: List<ComparisonPackage>?` and `savedPrivacy: Map<String, ComparisonSavedPrivacy>?`: null means unavailable; an empty collection means successfully read and empty. Rows carry package identity and ordered cells with user identity.

## Verification

All five `ProfileComparisonTest` regressions failed against an empty join scaffold, then passed after implementation. Tests exercise unavailable versus absent; removed versus absent with stale flags; both disabled and suspended; privacy identity and unavailable versus no saved policy; package joins and case-insensitive search across profile labels.

Successful command used `JAVA_HOME=/home/jordan/.local/share/jdk-21`:

```sh
./gradlew --offline testDebugUnitTest --tests io.github.samolego.canta.ops.ProfileComparisonTest
```

Result: **BUILD SUCCESSFUL**, 5 tests passed; the comparison screen compiled. The later bounded-controls refinement initially hit a Kotlin implicit receiver error for `maxHeight`; this was corrected by capturing `controlsHeight` directly in `BoxWithConstraints` before entering the Column receiver. The correction was verified by the later successful combined maintenance/comparison run: `testDebugUnitTest --tests io.github.samolego.canta.util.BoundedLogBufferTest --tests io.github.samolego.canta.util.BloatListUrlPolicyTest --tests io.github.samolego.canta.ops.ProfileComparisonTest` (13 tests passed, including all 5 comparison tests). The final screen compiles. The comparison-only diff check passed. Earlier shared-build interruptions from the missing `inventory_failed` resource and duplicate `app_version` resource were resolved by the parent.

No emulator actions, device changes, commits or publishing were performed. The parent owns final full-suite/build/lint checks, navigation integration, emulator observation of multi-profile/disconnected behavior, and normal/enlarged-text layout verification. Default strings are English and use normal Android fallback for untranslated locales.

## Final parent acceptance

Parent integration is complete. See [the final validation report](2026-09-26-audit-validation.md) for 156 passing JVM tests, passing debug/release builds and lint (zero errors), 28 distinct passing focused emulator tests, actual work-profile navigation, normal/enlarged-text inspection, retained evidence and explicit platform/performance limits. Earlier pending entries above describe historical checkpoints. No additional worker implementation remains.
