# Native Android UI refresh

Cana now uses Android's sans-serif typeface and the Material 3 type scale for interface text. This replaces the app-wide JetBrains Mono overrides in Compose, the Android window theme, and HTML app descriptions. The Cana wordmark retains its branded lettering and Dracula colors remain the app palette.

The sweep follows the [Android Material 3 guidance](https://developer.android.com/develop/ui/compose/designsystems/material3) and [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults): native text roles and line heights, tonal surfaces, clear hierarchy, named controls, scalable layouts, and reachable touch targets.

## Changes

- App names can occupy two lines in the inventory. Long press exposes its selection action to accessibility services.
- Search uses a rounded tonal field, with an accessible name that remains present after entering a query. The profile header measures the actual branded typeface independently of interface text.
- Settings place supporting text directly below each title. Enlarged switch rows use the full width for titles and descriptions, with the switch placed below. Sections are announced as headings, navigation rows use directional chevrons, and the project link has a 48dp minimum touch target.
- Presets, history, privacy, and device settings use consistent rounded tonal cards. Preset names can wrap; dialog headings use Material's default weight.
- Informational banners use neutral surfaces, while warning and error colors retain their existing meaning. Badges have more vertical breathing room; disclosures announce their expanded state and action.
- App details use native description text with a 24sp line height and a title that honors the user's text scale. Logs use the same spacing and shape language as other screens.

## Verification

The required command, with JDK 21 and Android SDK 36, passed on the final app source:

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest
```

All 248 unit tests passed. Release lint reported 0 errors, 742 warnings, and 1 hint. Both debug APKs built successfully. See [final Gradle output](verification/gradle-final.log).

No version properties, release tags, or signing configuration are changed by this refresh.

## Source review and accessibility regression

Independent source review found that an explicit content description on the editable search field could override its query for Android accessibility traversal. Search now uses Material's persistent label. The regression sends Android's real character-navigation action with selection extension, then checks that the query selection spans the first character and the query remains unchanged. Before the fix the action check failed with a collapsed `TextRange(1, 1)` instead of `TextRange(0, 1)`; see [the red result](verification/search-accessibility-red.log). A second source review found no remaining material issues.

## Device coverage

The disposable Android 15 / API 35 emulator uses software rendering and CPU emulation. The normal-text walkthrough ran at 360 × 800dp and passed all four tests: one complete navigation sweep and three populated-state tests. It covered inventory, removed apps, search and its keyboard, filters, hidden selections, confirmation dialogs, app details and native HTML text, logs, settings, device settings, presets, history, privacy, comparison, preset import/review, restore confirmation, and onboarding. [Normal test output](verification/normal-ui.log), [normal screenshots](normal), and [populated screenshots](normal-populated).

The normal walkthrough preceded the final search-label correction; its search screenshot records that intermediate appearance. The large-text walkthrough verifies the corrected search field. At 200%, visual review also exposed a switch-row title wrapping a single final letter beside its switch. The final adaptive settings layout addresses that by placing the switch below the text; its final verification is recorded below.

Early attempts encountered Android startup/focus ANRs and a real package-inventory scan exceeding 90 seconds. After the emulator settled, the complete normal run passed with a 300-second allowance for that scan only. UI, focus, animation, and keyboard timeouts were unchanged. The scan allowance is an optional instrumentation argument; the default remains 90 seconds. Earlier failures are retained in the verification directory.

These walkthroughs use fixtures and do not execute package removals, privacy changes, or preset imports. They do not establish physical-device performance, OEM-specific rendering, or the quality of spoken TalkBack output.


The focused checks covered 17 cases: scalable banners and tracker controls, a large-text RTL header, unified toggle targets, search lifecycle and accessibility selection, size labels, profile-scoped and hidden selection behavior, and navigation snackbar delivery. The first run passed 15 cases, with the new search test reading the asynchronously published accessibility tree too early and one selection test finding no Compose activity hierarchy. The search test now waits for Android to expose the query and cursor position. All nine search/selection cases passed in the retry after precompiling the emulator apps; the remaining eight cases passed in the first run. See [first run](verification/focused-first-run.log) and [successful retry](verification/focused-retry.log).

The complete 320 × 800dp / 200% run passed all four tests, including comparison inventory loading and the populated preset, history, and privacy states. See [large-text test output](verification/large-ui.log) and [populated large-text screenshots](large-populated). Visual review checked the main list, labeled search, filter sheet, selection review, app title/details, settings, device controls, comparison, keyboard-open import, restore confirmation, onboarding, and populated cards.

The final adaptive settings build passed both toggle tests and the secondary-screen/dialog walkthrough at 320dp / 200%: three tests, zero failures. The title “Require authentication” now wraps into two complete words with its switch below. This final suffix run used `startAt=secondary` and `captureComparison=false`; the complete preceding run already passed comparison. See [final device output](verification/secondary-final.log). The [large-text gallery](large) combines the unchanged main/detail/comparison captures from the complete run with the final secondary-screen and dialog captures.

Representative final large-text captures: [search](large/02-search.png), [app details](large/09-app-overview.png), [settings](large/12-settings.png), [device settings](large/14-device-settings.png), [keyboard-open import](large/20c-preset-import-keyboard.png), [restore confirmation](large/23-restore-confirmation.png), [presets](large-populated/01-presets-populated.png), and [history](large-populated/04-history-partial-status.png).

Both source reviews approved the final changes. The required Gradle checks passed again after the adaptive settings change. All requested changes are ready for the existing master-branch release workflow.
