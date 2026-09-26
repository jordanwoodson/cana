# Preset lifecycle and import review (Task 5)

Implemented 2026-09-26. No commits, publishing, physical-device access or package/privacy mutations were performed.

## Delivered

- **M2:** `PresetStore.getInstance(context)` retains only the application-backed DataStore. `initialize()` owns one application-scope migration/collector and returns the same read-only state flow on repeated calls. `PresetsViewModel.initialize(context)` is idempotent and collects that shared state once, without nested `stateIn` collectors. Library loading and writes have independent busy state.
- **F5 import:** Clipboard/text parsing leads to an explicit review dialog. No import writes occur during parsing, availability checks, rendering, or Cancel. UUID collisions show Update instead of Import. The review includes removal additions/deletions, privacy settings before/after, changed metadata/profile hint, resulting actions, target profile identity, missing targets, already-removed targets and unknown inventory/profile kind.
- `PresetStore.saveReviewedImport(review)` atomically compares the current same-UUID entry against `review.previous`. A concurrent edit or newly created UUID collision returns `REVIEW_CHANGED` without writing; the view model refreshes the diff and requires another confirmation. The exact captured `review.preset` is persisted on confirmation.
- Preset UUIDs now round-trip in JSON so revised imports can identify existing presets. Legacy files with no/blank ID still get a UUID; existing string/object app arrays, optional/future profile kinds, privacy flags, validation limits, and removal/privacy conflict checks remain supported.
- Legacy protobuf UUID migration retains preset contents. Persistence regression covers the exact reviewed privacy/profile values after reopening DataStore.
- New preset creation captures removed apps from unfiltered `allApps`, so list filters do not drop saved targets. Edit dialogs have an edit title.

## Integration interfaces

`ops/PresetPreview.kt` provides read-only policy:

```kotlin
PresetPreview.forProfiles(
    preset: CantaPresetData,
    inventories: List<PresetProfileInventory>,
): List<PresetProfilePreview>

PresetProfileInventory(
    userId: Int,
    name: String?,
    kind: String?,
    installedPackages: Set<String>? = null,
    knownPackages: Set<String>? = installedPackages,
)
```

Null package sets mean unavailable, never an empty known profile. `knownPackages` should include retained/uninstalled packages. The result exposes `inventoryAvailable`, `profileMismatch`, `profileKindUnknown`, `missingPackages`, and `alreadyRemovedPackages`, retaining `userId/name/kind`. Missing privacy targets are those not installed. A removal target that is known but not installed is already removed.

`ui/dialog/preset/PresetImportReviewDialog.kt` exposes:

```kotlin
@Composable fun PresetProfilePreviewContent(profiles: List<PresetProfilePreview>)
@Composable fun PresetActionsContent(preset: CantaPresetData)
```

Task 4 owns adding the policy/content to `PresetApplyDialog` and the batch preflight. These functions perform no reads, writes or operations. The import path currently reads the captured profile using `getAllPackagesInfo(userId)` on IO. Parent explicitly accepted this package-only lookup pending a fast snapshot API in the shared inventory repository.

## Files owned/changed

- `app/src/main/java/io/github/samolego/canta/data/PresetStore.kt`
- `app/src/main/java/io/github/samolego/canta/ui/viewmodel/PresetsViewModel.kt`
- `app/src/main/java/io/github/samolego/canta/ui/screen/PresetsPage.kt`
- `app/src/main/java/io/github/samolego/canta/ui/dialog/preset/PresetEditDialog.kt`
- `app/src/main/java/io/github/samolego/canta/ui/dialog/preset/PresetsDialog.kt`
- New `app/src/main/java/io/github/samolego/canta/ops/PresetPreview.kt`
- New `app/src/main/java/io/github/samolego/canta/ui/dialog/preset/PresetImportReviewDialog.kt`
- `app/src/main/java/io/github/samolego/canta/util/PresetJson.kt`
- New `app/src/main/res/values/strings_preset_improvements.xml`
- `app/src/test/java/io/github/samolego/canta/ops/PresetJsonTest.kt`
- New `app/src/test/java/io/github/samolego/canta/ops/PresetPreviewTest.kt`
- New `app/src/test/java/io/github/samolego/canta/data/PresetStoreTest.kt`
- New `app/src/androidTest/java/io/github/samolego/canta/ui/PresetImportUiDeviceTest.kt`

## Validation evidence

Initial regression: `PresetJsonTest.exportedIdentityAllowsAnImportedRevisionToMatchTheExistingPreset` failed with a UUID comparison failure against the prior export/import behavior, then passed after UUID preservation. New preview/store tests initially could not compile because their production interfaces did not yet exist. After implementation, `PresetStoreTest.cancelDoesNotWriteAndConfirmationPersistsExactlyTheReviewedRevision` failed because the persisted profile kind was null; explicitly addressing the extension receiver in the protobuf builder fixed the failure.

Final successful command, with `JAVA_HOME=/home/jordan/.local/share/jdk-21`:

```sh
./gradlew --offline testDebugUnitTest \
  --tests io.github.samolego.canta.ops.PresetJsonTest \
  --tests io.github.samolego.canta.ops.PresetPreviewTest \
  --tests io.github.samolego.canta.data.PresetStoreTest \
  compileDebugAndroidTestKotlin
```

Result: **BUILD SUCCESSFUL**, 16 focused JVM tests (6 JSON, 6 preview, 4 store); Android UI test sources compile. `git diff --check` passed. Existing Gradle deprecation warnings remain. A previous attempt was temporarily blocked by incomplete shared BatchCoordinator test interfaces; those resolved in the successful run.

`PresetImportUiDeviceTest` covers visible additions/removals/missing packages/profile mismatch; Cancel preserves storage; Update saves exactly the reviewed content; checking inventory disables confirmation and permits Cancel. These tests use only temporary preset storage and perform no privileged device changes. They were **compiled but not executed** by this worker.

## Remaining parent integration/verification

- Integrate preview policy/content into the final apply/batch preflight (parent-owned files).
- Centralize the profile package-only read when the shared inventory snapshot API is ready.
- Run the full JVM suite/build/lint after all workers finish, and execute the two new UI acceptance tests on the emulator. Observe import review at enlarged text and across rotation/navigation.
- New resource text is English in the default locale and falls back for other locales; no claim of translated content.

## Final parent acceptance

Parent integration is complete. See [the final validation report](2026-09-26-audit-validation.md) for 156 passing JVM tests, passing debug/release builds and lint (zero errors), 28 distinct passing focused emulator tests, actual work-profile navigation, normal/enlarged-text inspection, retained evidence and explicit platform/performance limits. Earlier pending entries above describe historical checkpoints. No additional worker implementation remains.
