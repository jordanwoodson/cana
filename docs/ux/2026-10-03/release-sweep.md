# October 3 UI release sweep

Cana's interface was reviewed and simplified while retaining its Dracula palette, JetBrains Mono, and existing app-management workflows. This report distinguishes rendered evidence, behavioral tests, and source review. It does not treat a screenshot of one state as proof of every path through a feature.

## Changes

- Inventory tabs keep their counts readable at large text sizes. App rows wrap risk and state badges and keep APK size separate. Search closes consistently, including a query restored from a saved view. Filters put frequently used choices before saved views.
- Selection adapts to available width while retaining hidden-selected counts, review, deselection, clear, and the complete action menu. The main scaffold responds to the keyboard so selection controls and the preset-edit Save action can remain reachable; an added native-position assertion checks the keyboard-visible footer. The follow-up scale runs are tracked below.
- App details wrap package identity, group metadata, and distinguish destructive actions. Components use one scrolling list, expose retry after errors, and offer a connection action while Shizuku is unavailable.
- Labeled checkboxes and switches expose one named toggle target. Long settings descriptions use the available width. Device choices switch from segments to full-width choices when their measured labels do not fit.
- Confirmation, import, undo, and privacy warning bodies scroll independently of their actions. Full target identities remain reachable. Preset import accommodates the keyboard; preset deletion asks for confirmation, and the former “Share” action accurately says it copies to the clipboard.
- History and Logs wrap metadata and describe their expand/copy controls. Privacy groups network and metered restrictions under one app/profile while retaining each restriction's state, review, and forget action.
- Welcome has step-back navigation, preserves acknowledgment, and resets content scrolling when the step changes. Settings opens the Shizuku checklist before attempting permission grants.
- Comparison keeps controls from consuming the whole screen, provides a connection action, and measures profile/status column widths so labels remain readable with horizontal scrolling.

These are changes established by source review. The evidence below states which outcomes were also exercised or captured.

## Completed checks

The [final local Gradle gate](verification/build-gate.log), after the keyboard and preset-edit copy adjustments, ran `:app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest` successfully. Unit-test XML records **237 tests in 59 suites, with no failures, errors, or skips**. The latest completed release-lint XML has **0 errors, 728 warnings, and 1 hint**; 583 warnings are `MissingTranslation`. The build completed successfully in 31 seconds. This is a passing gate, not a claim of warning-free lint or complete localization.

A [focused emulator run](verification/focused-ui.log) passed **21 UI tests** covering search close/restored queries, category and scope combinations, hidden selection review/deselection, profile-scoped collection state, unavailable-action preflight requests, unavailable usage filters, named toggles, Shizuku authorization/disconnected recovery, reviewed preset import, app-size labels, and snackbar delivery after navigation. These tests provide behavioral evidence beyond the screenshots; they do not certify live privileged operations.

| Final screenshot run | Result | Scope |
| --- | --- | --- |
| 360dp width, native font scale 1.0 | [4 tests passed](verification/sweep-font1.0.log) | General walkthrough plus three populated-fixture tests |
| 360dp width, native font scale 1.5 | [1 test passed](verification/sweep-font1.5.log) | General walkthrough |
| 360dp width, native font scale 2.0 | [4 tests passed](verification/sweep-font2.0.log) | General walkthrough plus three populated-fixture tests |

The runs use an isolated Android 15 emulator. The general suite uses real Compose screens with fixture inventory and import/restore data, the actual diagnostic log buffer, and genuine disconnected Shizuku states. It refreshes real local inventory for comparison. It executes no package, system-setting, or privacy operation; it restores its temporary onboarding preference, inventory, and clipboard. The populated suite temporarily seeds preset/history/privacy stores, reviews their UI, cancels delete/forget/undo confirmations, and restores the stores. Its records are **test fixtures, not evidence of successful live operations**.

Completed instrumented-run logs are preserved in [verification/](verification/), including failed attempts needed to interpret the retries below. Unit XML and lint XML are under `app/build/test-results/testDebugUnitTest/` and `app/build/reports/lint-results-release.xml`. The completed build gate is archived alongside those runs.

## Screenshot evidence map

Each general/populated PNG has a neighboring semantics dump with native width/font-scale metadata. General captures are in [font 1.0](screenshots/ui-sweep/360dp-font1.0/), [font 1.5](screenshots/ui-sweep/360dp-font1.5/), and [font 2.0](screenshots/ui-sweep/360dp-font2.0/). Populated fixtures are in [font 1.0](screenshots/ui-populated/1.0/) and [font 2.0](screenshots/ui-populated/2.0/). The run table above records the completed runs that produced these fixture/disconnected sets; the additional keyboard-position checks are recorded separately below.

| Flow | Recorded evidence and limits |
| --- | --- |
| Inventory, filters, selection | General `01`–`08`: Installed/Removed, open search, top/category filters, selected/hidden counts, selection review and action menu. [Hidden selection](screenshots/ui-sweep/360dp-font1.0/08-hidden-selection.png) is captured after the keyboard closes. Saved-view persistence and the live profile picker are not established by these images. |
| App details | General `09`–`11`: Overview top/bottom, readable identity, Privacy and Components. The latter two show disconnected states; connected controls and successful changes require separate evidence. |
| Settings and Logs | General `12`, `12a`, `12b`, `13`: Settings, real log rows, an expanded message, and About content including theme/version. Copy-all logs is checked against clipboard contents and the original clipboard is restored. Long-press copy of one entry, data-source editing, grants, and restore export are not part of this walkthrough. |
| Device settings | General `14`–`15`: top and lower controls, including Data Saver. Readable options and available read-only values are shown; applying DNS/scanning/data-saver settings, consent, and revert are not executed. |
| Preset import and library | General `16`, `20`–`22`: library, clipboard/text import, [keyboard-open import](screenshots/ui-sweep/360dp-font1.0/20c-preset-import-keyboard.png), keyboard dismissal, review, and profile-mismatch/availability warnings. Populated `01`–`03` show a saved preset, its action menu, and deletion review; cancellation preserves the library. Create/edit/apply and preset clipboard copying are not completed by this screenshot suite. |
| History | General `17` shows the fresh empty state. Populated `04`, `05`, `05b` show a partial-result fixture, undo review, and reachable target identity. Cancel preserves history. No recovery operation runs. |
| Privacy dashboard | General `18` shows disconnected/empty feedback. Populated `06`–`09` show two grouped restrictions, their distinct states, both forget confirmations/guidance, and review. Cancel preserves both records. Live enforcement is not inferred from saved fixture state. |
| Comparison | General `19` captures the settled single-profile/disconnected result after loading text disappears and Refresh becomes enabled. Live multi-profile comparison is a separate connected check. |
| Confirmation and welcome | General `23`/`23b` show restore confirmation and the full target package after scrolling. General `24`, `25`, `25b` check disabled Continue before acknowledgment, the Shizuku step starting at the top, Back preserving acknowledgment, and forward navigation. Restore is never confirmed. Other warning/update-consent variants and connected welcome completion are not covered here. |

The screenshot harness waits for keyboard visibility and native IME animations, stable expanded-sheet bounds, comparison loading completion, and native rendering frames. Earlier boot-screen, moving-sheet, keyboard-covered, and loading captures were rejected as final evidence and rerun. Review included normal and enlarged text; screenshot assertions are not a substitute for inspecting the images.

## Connected tests and native walkthrough

All **13 connected test cases have now passed across the connected run and targeted retry**. The [initial connected run](verification/connected-ui-initial.log) passed 12 of 13; the [safety/source retry](verification/connected-safety-and-source.log) passed the remaining keyboard-safety case plus two tracker-source regression tests. This is not reported as a single clean 13-test run.

The connected cases exercised actual emulator state with a test APK installed in Personal and Work profiles:

- Work-profile disabling followed by snackbar Undo, and History Undo, restore the fixture without changing its Personal counterpart. Work usage-sort availability is checked.
- Preset application requires the all-profile acknowledgment, names affected profiles, applies metered restrictions, and undoes the test changes. A separate roundtrip/application check preserves the Personal profile while applying and restoring Work restrictions.
- Runtime permission/background values are visible; opening background-change review and canceling leaves live state unchanged. Device tests check invalid DNS input, connectivity-warning cancellation, a scan-setting change, and restoration from its undo record.
- Unsafe hidden selection defaults to Disable and retains all alternate actions. Ordinary app components expose Android's restriction and disabled controls. Active-keyboard removal confirmation remains disabled until warnings are acknowledged; the test collects consent rather than uninstalling the keyboard.
- Updated-system-app cleanup starts with a shared update excluded and requires explicit selection/consent. The cleanup UI tests do not remove the update. Tracker-source tests separately verify that a draft survives recreation and cannot overwrite a source before its saved value loads.

The [native connected captures](screenshots/connected/) use the actual app, authorized Shizuku, and Personal/Work inventories, with XML accessibility dumps alongside the PNGs. Connected operation-test results above remain distinct from screenshots of review dialogs.

| Native evidence | Observed flow and scope |
| --- | --- |
| `01`–`03` | Shizuku was authorized through its manager; the first capture shows the running-service checklist before authorization, then Cana offered Personal/Work onboarding, and opened the live Work inventory. |
| `04`–`05` | The installed test APK's services/activity/provider/receiver are listed; component-change review names the package and component. Review was canceled. |
| `06`–`07` | [Metered and network controls](screenshots/connected/07-privacy-network.png) show their explanations and live “Not restricted”/“Not blocked” state. The recaptured `06` shows loaded runtime permission labels, Denied states, the disabled Revoke 0 action, and background controls. Connected tests additionally verify their behavior. |
| `08`–`10` | The live profile picker offers Personal and Work. [Comparison](screenshots/connected/09-comparison.png) shows differences across both inventories; the second capture verifies horizontal access to the profile columns. |
| `11`–`12` | Secure settings and Usage access show Allowed. The recommendation-source editor opens with the current URL and is canceled without saving. |
| `13` | [Recovery export](screenshots/connected/13-recovery-export.png) opens Android's save chooser. The operator separately verified a saved `cana-restore.sh` file of **5,588 bytes** on the isolated emulator. The script was not executed. |
| `14` | The create-preset form shows its name/description, current-profile removal count, profile-kind option, and privacy option. The native walkthrough created “Release test,” edited its description to “Reviewed on emulator,” and saved the fixture as its one selected app. The replaced `15` shows Save fully above the open keyboard; tapping it returned to the library with the fixture still selected. `19` shows the edited values and the corrected editing instructions. `20` shows deletion review; confirming it returned to No presets and displayed Preset deleted, while the fixture remained installed. |
| `16` | [Copy/import roundtrip review](screenshots/connected/16-preset-copy-import-review.png) identifies the existing preset's same ID, edited description, and exact fixture package, with no removal/privacy-action differences. Import review was canceled. This verifies copying through the clipboard without applying a removal. |
| `17`–`18` | A “Fixture view” saved the `fixture` query. Closing search restored the 245-app list; reopening the saved view restored the query and one matching test app, shown in [restored saved view](screenshots/connected/18-restored-saved-view.png). |

Retries are preserved rather than counted as passes: the [optional prerequisite attempt](verification/optional-prerequisites.log) failed the update-cleanup case before Shizuku authorization and the updated-system-app fixture were ready; both cleanup cases passed in the later connected run. The first keyboard-safety test assumed `com.android.inputmethod.latin`, which is absent on this Google emulator. It now queries the active keyboard and passed on retry. The rejected loading and keyboard-obscured native captures were replaced before committing the evidence.

## Keyboard regression and limits

The final keyboard-position assertion passed complete general walkthroughs at [font 1.0](verification/keyboard-font1.0.log), [font 1.5](verification/keyboard-font1.5.log), and [font 2.0](verification/keyboard-font2.0.log), one test per run. It compares the selection action’s window bounds against the keyboard edge from native IME insets before capturing `08a-hidden-selection-keyboard`. The preserved general screenshot sets above were taken immediately before this final keyboard-only layout change; these three added captures document the changed state. Native capture `15` verifies the preset Save action too. Captures `06` and `15` were replaced with settled final images; `19` verifies final edit-preset copy. All were visually reviewed.

Independent source reviews found no remaining concrete regressions in the changed action paths. The local release gate passes, rendered compact/large-text layouts were reviewed, and the connected/native flows above are complete.

Long-press copying of a single log entry and every data-source validation/error path were not exercised end to end in this sweep. Preset apply, profile isolation, warning consent, and undo now have the specific connected-test evidence above; this does not certify every package, privilege level, or recovery situation.

This review does not cover physical/OEM devices, every Android version, every translation, or a formal TalkBack/switch-access audit. Large-text and semantics checks improve confidence in the tested English layouts and named controls but do not establish full accessibility conformance. Release lint's translation warnings remain an explicit limitation.

## CI and release — pending

Local checks above are recorded before publication. Root will append the pushed `master` commit, GitHub Actions release-build result, and resulting release identifier/link. Version numbers, tags, releases, and the signing key are not changed manually; the existing release workflow assigns the next `3.2.2-cana.N` and signs the update.
