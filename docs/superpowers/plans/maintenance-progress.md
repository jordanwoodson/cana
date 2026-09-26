# Bounded diagnostics and explicit list-URL settings (Task 7)

Implemented in the assigned logging/settings files only. No emulator/device changes, commits or publishing were performed.

## Delivered

- **M5 bounded diagnostics:** `BoundedLogBuffer` retains the newest 500 entries, with at most 128 tag characters and 4,096 message characters per entry. Truncation is visible. It provides synchronized append/snapshot operations and immutable snapshots, so concurrent log producers cannot exceed the count limit or mutate an already-read list.
- `LogUtils` uses that buffer, an application-lifetime owned coroutine scope and one conflated update channel. A burst of diagnostics cannot queue a coroutine per log entry. The observable UI list is published on Main, and the formatter is immutable/thread-safe `DateTimeFormatter`. Existing `d/i/w/e`, `getLogs`, `LogEntry`, and `LogLevel` APIs remain available. Native Android error logging still receives the throwable; the in-memory diagnostic text is capped.
- **M7 explicit URL Save:** Settings shows the current saved URL. Opening it creates a separate editable draft in the retained settings view model. Typing changes only the draft. Cancel resets the draft without writing; an unchanged Save closes without clearing cache metadata. A changed valid Save calls the existing `SettingsStore.setBloatListUrl` exactly once. A failed write keeps the dialog/draft available and reports that the saved setting is unchanged.
- `BloatListUrlPolicy.normalize` accepts trimmed HTTP/HTTPS URLs with a valid host and valid optional port. It rejects whitespace/control characters, invalid or absent hosts, credentials, fragments, unsupported schemes, oversized input and invalid ports. It performs no network request. HTTP is retained because the existing downloader supports it.
- “Use default URL” changes the draft; the user reviews it and presses Save to persist the reset. This preserves Cancel semantics for reset as well.
- **M8 obsolete control:** Removed “Confirm uninstallations” from Settings and its unused view-model observer/setter. `SettingsStore` and protobuf legacy fields remain untouched/readable. Expand/collapse descriptions in the edited screen are localized.

## Files

- Updated `app/src/main/java/io/github/samolego/canta/util/LogUtils.kt`
- New `app/src/main/java/io/github/samolego/canta/util/BoundedLogBuffer.kt`
- New `app/src/test/java/io/github/samolego/canta/util/BoundedLogBufferTest.kt`
- Updated `app/src/main/java/io/github/samolego/canta/ui/screen/SettingsPage.kt`
- Updated `app/src/main/java/io/github/samolego/canta/ui/viewmodel/SettingsViewModel.kt`
- New `app/src/main/java/io/github/samolego/canta/util/BloatListUrlPolicy.kt`
- New `app/src/test/java/io/github/samolego/canta/util/BloatListUrlPolicyTest.kt`
- New `app/src/main/res/values/strings_maintenance.xml`

The URL editor has injectable persistence and exposes `state: StateFlow<BloatListUrlEditorState>` with `begin`, `edit`, `cancel`, `observeSaved`, and suspend `save`. The SettingsViewModel owns it and binds its persistence to the existing SettingsStore. The logging helper has no Android dependency.

## Validation evidence

All 8 maintenance tests failed against initial scaffolds, then passed after implementation. The tests cover newest-entry eviction, snapshot isolation, concurrent producer limits, tag/message bounds, accepted/rejected URLs, draft/cancel isolation, invalid and failed saves, unchanged saves, explicit default replacement, and incoming saved state while a draft is open.

Final successful command with `JAVA_HOME=/home/jordan/.local/share/jdk-21`:

```sh
./gradlew --offline testDebugUnitTest \
  --tests io.github.samolego.canta.util.BoundedLogBufferTest \
  --tests io.github.samolego.canta.util.BloatListUrlPolicyTest \
  --tests io.github.samolego.canta.ops.ProfileComparisonTest
```

Result: **BUILD SUCCESSFUL**, 13 tests pass (3 log-buffer, 5 URL-editor/policy, 5 comparison). All changed production Kotlin/resources compile, including the final comparison layout correction. A transient LogUtils delegated-property getter clash was caught during integration and corrected by renaming the backing UI list to `entries`.

Parent owns final full-suite/build/lint and emulator verification. Suggested settings observation: edit/cancel leaves the displayed URL unchanged; invalid Save retains the editor with an error; default + Save persists; reopen shows the saved value. Observe logs during background operations and copy the latest bounded list. These UI checks were not run by this worker.

No global cleanup was performed. Existing downloader/cache behavior and portable settings storage are unchanged. New strings are English default resources with normal fallback for untranslated locales. The existing `SettingsScreen` version-tap callback signature was left unchanged during shared-build stabilization, per coordination with the parent.

## Final parent acceptance

Parent integration is complete. See [the final validation report](2026-09-26-audit-validation.md) for 156 passing JVM tests, passing debug/release builds and lint (zero errors), 28 distinct passing focused emulator tests, actual work-profile navigation, normal/enlarged-text inspection, retained evidence and explicit platform/performance limits. Earlier pending entries above describe historical checkpoints. No additional worker implementation remains.
