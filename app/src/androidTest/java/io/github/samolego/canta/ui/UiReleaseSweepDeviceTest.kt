package io.github.samolego.canta.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.BuildConfig
import io.github.samolego.canta.R
import io.github.samolego.canta.data.SettingsStore
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ops.InventoryRepository
import io.github.samolego.canta.ops.InventoryState
import io.github.samolego.canta.ops.PresetPreview
import io.github.samolego.canta.ops.PresetProfileInventory
import io.github.samolego.canta.ui.dialog.PackageActionConfirmation
import io.github.samolego.canta.ui.dialog.preset.ImportPresetDialog
import io.github.samolego.canta.ui.dialog.preset.PresetImportReviewDialog
import io.github.samolego.canta.ui.screen.WelcomePage
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import io.github.samolego.canta.ui.viewmodel.PackageAction
import io.github.samolego.canta.ui.viewmodel.PackageActionRequest
import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.InstallData
import io.github.samolego.canta.util.LockdownSettings
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.util.apps.AppInfo
import io.github.samolego.canta.util.apps.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Visual release smoke coverage. Run on an emulator at each desired native font scale/width.
 * Screenshots and semantics are written to targetContext.getExternalFilesDir(null)/ui-sweep.
 *
 * No package, system, privacy, preset, or history operations are executed. Inventory fixtures
 * are in memory only, and the temporary onboarding preference is restored in finally.
 * Shizuku-dependent screens show their real disconnected states when Shizuku is absent.
 * This deliberately does not replace connected operation/device tests.
 * Optional instrumentation argument startAt=details, secondary, or dialogs resumes a partial sweep;
 * omit it for complete coverage. Screenshots from a resumed run cover only that suffix.
 * captureComparison=false skips the comparison screen's real package-inventory refresh.
 * inventoryTimeoutMillis can allow a slower real package scan on software-only emulators;
 * it does not change UI, focus, animation, or keyboard assertion timeouts.
 */
@SdkSuppress(minSdkVersion = 30)
class UiReleaseSweepDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val userId get() = UserProfile.currentUserId
    private var host by mutableStateOf(Host.APP)
    private var restoreClipboard: (() -> Unit)? = null
    private val observedWindows = mutableSetOf<View>()
    private val imeAnimations = mutableSetOf<WindowInsetsAnimation>()
    private enum class Host { APP, WELCOME, RESTORE, IMPORT, IMPORT_REVIEW, EMPTY }

    @Test fun captureNonMutatingReleaseSweep() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Use an emulator for fixture screenshots" }
        val startAt = InstrumentationRegistry.getArguments().getString("startAt", "main")
        val captureComparison = InstrumentationRegistry.getArguments().getString("captureComparison", "true").toBooleanStrict()
        val inventoryTimeoutMillis = InstrumentationRegistry.getArguments()
            .getString("inventoryTimeoutMillis", "90000").toLong().also { require(it > 0) }
        require(startAt in setOf("main", "details", "secondary", "dialogs")) {
            "startAt must be main, details, secondary, or dialogs"
        }
        val settings = SettingsStore.getInstance()
        val originalOnboarding = settings.disableRiskDialogFlow.first()
        val inventory = inventoryStates()
        val originalInventory = withContext(Dispatchers.Main) { inventory.toMap() }
        val apps = fixtureApps()
        val model = withContext(Dispatchers.Main) { AppListViewModel() }
        val imported = CantaPresetData(
            "Work profile cleanup", "Remove optional apps and review saved privacy restrictions.", 1,
            setOf("example.optional.weather", "example.optional.partner"), uuid = "ui-sweep-only",
            lockdown = listOf(LockdownSettings("example.optional.analytics", denyMetered = true)),
            profileKind = "WORK",
        )
        val review = PresetPreview.reviewImport(imported, emptyList(), listOf(
            PresetProfileInventory(userId, "Personal", "PERSONAL", setOf("example.optional.weather")),
        ))
        try {
            settings.setDisableRiskDialog(true)
            withContext(Dispatchers.Main) {
                inventory[userId] = InventoryState(apps = apps, stale = false, loaded = true)
            }
            compose.setContent {
                CantaTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        when (host) {
                            Host.APP -> CantaApp(closeApp = {})
                            Host.WELCOME -> WelcomePage(model, onExit = {}, onFinished = {})
                            Host.RESTORE -> PackageActionConfirmation(
                                PackageActionRequest(PackageAction.REINSTALL, userId, apps.filter { it.isUninstalled }),
                                model, onDismiss = { host = Host.EMPTY },
                                onAgree = { _, _, _, _ -> error("The visual sweep must never execute an operation") },
                            )
                            Host.IMPORT -> ImportPresetDialog(
                                onDismiss = { host = Host.EMPTY },
                                onImportFromClipboard = { error("Clipboard import must not run") },
                                onImportFromText = { error("Import must not run") },
                            )
                            Host.IMPORT_REVIEW -> PresetImportReviewDialog(
                                review, checking = false, saving = false, error = null,
                                onConfirm = { error("The visual sweep must never save a preset") },
                                onDismiss = { host = Host.EMPTY },
                            )
                            Host.EMPTY -> Unit
                        }
                    }
                }
            }

            waitForDescription(R.string.search_action)
            if (startAt == "main") {
                compose.onNodeWithText(apps.first().name).assertIsDisplayed()
                capture("01-main")
                compose.onNodeWithText(context.resources.getQuantityString(R.plurals.tab_removed_count, 1, 1)).performClick()
                capture("01b-removed")
                compose.onNodeWithText(context.resources.getQuantityString(R.plurals.tab_installed_count, 5, 5)).performClick()

                observeImeWindow()
                description(R.string.search_action).performClick()
                compose.onNode(hasSetTextAction()).performTextReplacement("Android")
                waitForIme(visible = true)
                compose.onNode(hasSetTextAction()).performImeAction()
                waitForIme(visible = false)
                capture("02-search")
                description(R.string.close_search).performClick()

                description(R.string.filters_action).performClick()
                waitForExpandedFilters()
                capture("03-filters-top")
                compose.onNodeWithText("AOSP").performScrollTo().assertIsDisplayed()
                capture("04-filters-categories")
                text(R.string.tracker_scan_all).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.tracker_scanned_count, 0,
                    apps.count { !it.isUninstalled })).assertIsDisplayed()
                capture("04b-filters-privacy")
                text(R.string.clear).performClick()
                compose.onNodeWithText(context.resources.getQuantityString(
                    R.plurals.show_app_count, apps.count { !it.isUninstalled }, apps.count { !it.isUninstalled },
                )).assertIsDisplayed().performClick()

                compose.onNodeWithContentDescription(context.getString(R.string.app_select_description, apps.first().name))
                    .performClick()
                capture("05-selection")
                compose.onNodeWithText(context.resources.getQuantityString(R.plurals.selection_selected_count, 1, 1))
                    .performClick()
                text(R.string.review_selection).assertIsDisplayed()
                capture("06-selection-review")
                text(R.string.close).performClick()
                description(R.string.selection_more_actions).performClick()
                capture("07-selection-actions")
                pressBack()
                observeImeWindow()
                description(R.string.search_action).performClick()
                compose.onNode(hasSetTextAction()).performTextReplacement("no matching fixture")
                waitForIme(visible = true)
                assertAboveIme(description(R.string.selection_more_actions))
                capture("08a-hidden-selection-keyboard")
                compose.onNode(hasSetTextAction()).performImeAction()
                waitForIme(visible = false)
                text(R.string.selection_hidden_short, 1, 1).assertIsDisplayed()
                capture("08-hidden-selection")
                description(R.string.close_search).performClick()
                description(R.string.clear_selection).performClick()
            }

            if (startAt == "main" || startAt == "details") {
                compose.onNodeWithText(apps.first().name).performClick()
                text(R.string.app_overview).assertIsDisplayed()
                capture("09-app-overview")
                text(R.string.open_android_settings).performScrollTo().assertIsDisplayed()
                capture("09b-app-overview-bottom")
                text(R.string.privacy).performClick()
                capture("10-app-privacy")
                text(R.string.components).performClick()
                capture("11-app-components")
                description(R.string.back).performClick()
            }

            if (startAt != "dialogs") {
                openPage(R.string.settings)
                capture("12-settings")
                inspectLogsFromSettings()
                compose.onNodeWithText("https://github.com/jordanwoodson/cana").performScrollTo().assertIsDisplayed()
                text(R.string.settings_theme_summary).assertIsDisplayed()
                text(R.string.app_version, BuildConfig.VERSION_NAME).assertIsDisplayed()
                capture("13-settings-about")
                description(R.string.back).performClick()
                openPage(R.string.device_settings)
                capture("14-device-settings")
                compose.onNodeWithTag("system-DATA_SAVER-true").performScrollTo().assertIsDisplayed()
                capture("15-device-settings-bottom")
                description(R.string.back).performClick()

                openPage(R.string.presets)
                capture("16-presets")
                description(R.string.back).performClick()
                openPage(R.string.history)
                capture("17-history")
                description(R.string.back).performClick()
                openPage(R.string.privacy_dashboard)
                capture("18-privacy-dashboard")
                description(R.string.back).performClick()
                // Comparison refreshes the real package inventory; keep it last in the main-app run.
                if (captureComparison) {
                    openPage(R.string.comparison_title)
                    compose.waitUntil(inventoryTimeoutMillis) {
                        compose.onAllNodesWithText(context.getString(R.string.comparison_loading)).fetchSemanticsNodes().isEmpty() &&
                            compose.onAllNodes(hasContentDescription(context.getString(R.string.comparison_refresh)) and isEnabled())
                                .fetchSemanticsNodes().size == 1
                    }
                    text(R.string.comparison_loading).assertDoesNotExist()
                    description(R.string.comparison_refresh).assertIsDisplayed().assertIsEnabled()
                    capture("19-comparison")
                    description(R.string.back).performClick()
                }

            }

            switchHost(Host.IMPORT)
            text(R.string.import_preset).assertIsDisplayed()
            capture("20-preset-import")
            compose.onNodeWithTag("preset-import-text").performClick()
            capture("20b-preset-import-text")
            observeImeWindow()
            compose.onNode(hasSetTextAction()).performClick().performTextReplacement("{}")
            waitForIme(visible = true)
            assertOnScreenAboveIme(text(R.string.import_preset))
            assertOnScreenAboveIme(text(R.string.preset_review_button))
            assertOnScreenAboveIme(text(R.string.cancel))
            capture("20c-preset-import-keyboard")
            pressBack()
            waitForIme(visible = false)
            capture("20d-preset-import-keyboard-dismissed")
            text(R.string.cancel).assertIsDisplayed().performClick()

            switchHost(Host.IMPORT_REVIEW)
            text(R.string.preset_review_import).assertIsDisplayed()
            capture("21-preset-import-review")
            text(R.string.preset_profile_mismatch).performScrollTo().assertIsDisplayed()
            capture("22-preset-import-review-availability")
            text(R.string.cancel).assertIsDisplayed().performClick()

            switchHost(Host.RESTORE)
            text(R.string.reinstall).assertIsDisplayed().assertIsEnabled()
            capture("23-restore-confirmation")
            compose.onNodeWithText(apps.single { it.isUninstalled }.packageName, useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed()
            capture("23b-restore-confirmation-target")
            text(R.string.cancel).assertIsDisplayed().performClick()

            switchHost(Host.WELCOME)
            text(R.string.welcome_continue).assertIsNotEnabled()
            capture("24-welcome-safety")
            text(R.string.welcome_acknowledge).performScrollTo().performClick()
            text(R.string.welcome_continue).assertIsEnabled().performClick()
            text(R.string.welcome_skip).assertIsDisplayed()
            // Do not scroll here: moving to a new step must reset the previous step's scroll.
            text(R.string.shizuku_requirement_description).assertIsDisplayed()
            capture("25-welcome-shizuku")
            description(R.string.back).assertIsDisplayed().performClick()
            text(R.string.welcome_step_one).assertIsDisplayed()
            text(R.string.welcome_continue).assertIsDisplayed().assertIsEnabled()
            text(R.string.welcome_acknowledge).assertIsOn()
            description(R.string.back).assertDoesNotExist()
            text(R.string.welcome_continue).performClick()
            text(R.string.welcome_step_two).assertIsDisplayed()
            text(R.string.shizuku_requirement_description).assertIsDisplayed()
            capture("25b-welcome-shizuku-after-back")
        } catch (failure: Throwable) {
            runCatching { capture("00-failure") }
            throw failure
        } finally {
            compose.runOnIdle { host = Host.EMPTY }
            restoreClipboard?.invoke()
            restoreClipboard = null
            settings.setDisableRiskDialog(originalOnboarding)
            withContext(Dispatchers.Main) {
                inventory.clear()
                inventory.putAll(originalInventory)
            }
        }
    }

    private fun text(resource: Int, vararg args: Any) = compose.onNodeWithText(context.getString(resource, *args))
    private fun description(resource: Int) = compose.onNodeWithContentDescription(context.getString(resource))

    private fun waitForDescription(resource: Int) {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription(context.getString(resource)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openPage(title: Int) {
        description(R.string.more_options_action).performClick()
        compose.onNode(hasText(context.getString(title)) and hasClickAction() and hasAnyAncestor(isPopup()))
            .performScrollTo().assertIsDisplayed().performClick()
        description(R.string.back).assertIsDisplayed()
        compose.onNode(isPopup()).assertDoesNotExist()
        // The app bar's layout directly contains Back and the title container. Body headings
        // may repeat the page title (for example Privacy dashboard's empty state).
        val appBar = hasAnyChild(hasContentDescription(context.getString(R.string.back)) and hasClickAction())
        compose.onNode(hasText(context.getString(title)) and !hasClickAction() and hasAnyAncestor(appBar))
            .assertIsDisplayed()
    }

    private fun inspectLogsFromSettings() {
        text(R.string.logs).performScrollTo().assertIsDisplayed().performClick()
        description(R.string.copy_logs).assertIsDisplayed()
        description(R.string.back).assertIsDisplayed()
        capture("12a-logs")

        // Read the real diagnostic buffer. The sweep does not inject or remove log records.
        val firstEntry = compose.runOnIdle { LogUtils.getLogs().firstOrNull() }
        if (firstEntry == null) {
            text(R.string.logs_empty_title).assertIsDisplayed()
        } else {
            val collapsed = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                context.getString(R.string.logs_entry_collapsed))
            val expanded = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                context.getString(R.string.logs_entry_expanded))
            // The oldest entry is the first row. Verify its identity before expanding it.
            compose.onAllNodes(collapsed and hasClickAction())[0].performScrollTo()
                .assertTextContains("${firstEntry.level} ${firstEntry.tag}")
                .assertTextContains(firstEntry.getFormattedTime()).performClick()
            compose.onNode(expanded and hasClickAction()).assertIsDisplayed()
                .assertTextContains(firstEntry.message)
            capture("12b-logs-expanded")

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val originalClip = compose.runOnIdle { clipboard.primaryClip }
            // Restore after the final capture so Android's restoration clipboard overlay cannot
            // cover later screenshots. The outer finally also runs this if an assertion fails.
            restoreClipboard = {
                instrumentation.runOnMainSync {
                    if (originalClip == null) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(originalClip)
                }
            }
            val expectedLogs = compose.runOnIdle { LogUtils.getLogs().joinToString("\n") { entry ->
                "[${entry.getFormattedTime()}] ${entry.level} ${entry.tag}: ${entry.message}"
            } }
            description(R.string.copy_logs).performClick()
            compose.waitUntil(5_000) {
                compose.runOnIdle {
                    clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                        ?.coerceToText(context)?.toString()?.contains(expectedLogs) == true
                }
            }
            text(R.string.log_copied).assertIsDisplayed()
            // Let copy feedback leave before capturing the next Settings section.
            // The v2 rule's virtual clock also drives SnackbarHost's timeout.
            compose.mainClock.advanceTimeBy(5_000)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(context.getString(R.string.log_copied)).fetchSemanticsNodes().isEmpty()
            }
        }
        description(R.string.back).performClick()
        text(R.string.settings_diagnostics).performScrollTo().assertIsDisplayed()
    }

    /** Observe the window before opening/hiding its IME; Compose idleness excludes platform animations. */
    private fun observeImeWindow() {
        compose.waitForIdle()
        // Dismissing a popup/dialog can briefly leave every app window without focus.
        // Wait for the actual focus handoff instead of observing a detached or background window.
        compose.waitUntil(10_000) {
            var observed = false
            instrumentation.runOnMainSync {
                val window = WindowInspector.getGlobalWindowViews().lastOrNull {
                    it.isAttachedToWindow && it.hasWindowFocus()
                }
                if (window != null) {
                    if (observedWindows.add(window)) {
                        window.setWindowInsetsAnimationCallback(object : WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                            override fun onPrepare(animation: WindowInsetsAnimation) {
                                if (animation.typeMask and WindowInsets.Type.ime() != 0) imeAnimations.add(animation)
                            }
                            override fun onProgress(insets: WindowInsets, runningAnimations: MutableList<WindowInsetsAnimation>): WindowInsets = insets
                            override fun onEnd(animation: WindowInsetsAnimation) { imeAnimations.remove(animation) }
                        })
                    }
                    observed = true
                }
            }
            observed
        }
    }

    private fun waitForIme(visible: Boolean) {
        compose.waitUntil(10_000) {
            var settled = false
            instrumentation.runOnMainSync {
                val focusedWindow = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
                val insets = focusedWindow?.rootWindowInsets
                settled = insets != null && insets.isVisible(WindowInsets.Type.ime()) == visible && imeAnimations.isEmpty()
            }
            settled
        }
        instrumentation.waitForIdleSync()
    }

    private fun assertAboveIme(node: SemanticsNodeInteraction) {
        compose.waitForIdle()
        var keyboardTop = 0
        instrumentation.runOnMainSync {
            val window = checkNotNull(WindowInspector.getGlobalWindowViews().lastOrNull {
                it.isAttachedToWindow && it.hasWindowFocus()
            })
            val insets = checkNotNull(window.rootWindowInsets)
            val imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
            check(insets.isVisible(WindowInsets.Type.ime()) && imeBottom > 0) {
                "The native keyboard must be visible for the occlusion check"
            }
            val location = IntArray(2)
            window.getLocationInWindow(location)
            keyboardTop = location[1] + window.height - imeBottom
        }
        val bounds = node.assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertTrue("Selection actions $bounds must be above the native keyboard at y=$keyboardTop",
            bounds.height > 0f && bounds.bottom < keyboardTop)
    }

    private fun assertOnScreenAboveIme(node: SemanticsNodeInteraction) {
        var windowTop = 0
        var keyboardTop = 0
        instrumentation.runOnMainSync {
            val window = checkNotNull(WindowInspector.getGlobalWindowViews().lastOrNull {
                it.isAttachedToWindow && it.hasWindowFocus()
            })
            val location = IntArray(2)
            window.getLocationOnScreen(location)
            windowTop = location[1]
            val insets = checkNotNull(window.rootWindowInsets)
            check(insets.isVisible(WindowInsets.Type.ime()))
            keyboardTop = windowTop + window.height - insets.getInsets(WindowInsets.Type.ime()).bottom
        }
        val bounds = node.assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertTrue("Dialog content $bounds at window y=$windowTop must fit above keyboard y=$keyboardTop",
            bounds.height > 0f && bounds.top + windowTop >= 0f && bounds.bottom + windowTop < keyboardTop)
    }

    private fun waitForExpandedFilters() {
        val title = compose.onNode(hasText(context.getString(R.string.filters_title)) and hasAnyAncestor(isDialog()))
        val clear = compose.onNode(hasText(context.getString(R.string.clear)) and hasAnyAncestor(isDialog()))
        val dialog = compose.onNode(isDialog())
        var lastBounds: List<androidx.compose.ui.geometry.Rect>? = null
        var stableSince = SystemClock.uptimeMillis()
        compose.waitUntil(30_000) {
            val viewport = dialog.fetchSemanticsNode().boundsInWindow
            val bounds = listOf(title.fetchSemanticsNode().boundsInWindow, clear.fetchSemanticsNode().boundsInWindow)
            val expanded = bounds[0].top >= viewport.top && bounds[0].top < viewport.top + viewport.height * 0.35f &&
                bounds[1].top > viewport.top + viewport.height * 0.6f && bounds[1].bottom <= viewport.bottom
            if (!expanded || bounds != lastBounds) {
                lastBounds = bounds
                stableSince = SystemClock.uptimeMillis()
            }
            expanded && SystemClock.uptimeMillis() - stableSince >= 300
        }
        title.assertIsDisplayed()
        clear.assertIsDisplayed()
    }

    /** Semantics can settle before the Android window has submitted its next drawn frame. */
    private fun awaitRenderedFrames() {
        val drawn = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val window = checkNotNull(WindowInspector.getGlobalWindowViews().lastOrNull { it.isAttachedToWindow })
            window.postOnAnimation { window.postOnAnimation { drawn.countDown() } }
        }
        assertTrue("The screenshot window did not render", drawn.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        // Force and copy the current app frame before taking the display capture.
        // Animation callbacks alone can precede RenderThread on software emulators.
        val roots = compose.onAllNodes(isRoot())
        roots.fetchSemanticsNodes().indices.forEach {
            roots[it].captureToImage().asAndroidBitmap().recycle()
        }
    }

    private fun switchHost(next: Host) {
        compose.runOnIdle { host = next }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        awaitRenderedFrames()
        val config = context.resources.configuration
        val folder = File(context.getExternalFilesDir(null), "ui-sweep/${config.screenWidthDp}dp-font${config.fontScale}")
        check(folder.exists() || folder.mkdirs())
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(folder, "$name.png").outputStream().use {
                assertTrue("Could not save $name", screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
        val roots = compose.onAllNodes(isRoot())
        // Capture the app directly only when there is one root. Dialogs and
        // popups use the display capture above, which includes all windows.
        if (roots.fetchSemanticsNodes().size == 1) {
            val rootBitmap = roots[0].captureToImage().asAndroidBitmap()
            try {
                File(folder, "$name-window.png").outputStream().use {
                    assertTrue("Could not save $name window", rootBitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { rootBitmap.recycle() }
        }
        File(folder, "$name.txt").writeText(
            "Native configuration: ${config.screenWidthDp} x ${config.screenHeightDp}dp; fontScale=${config.fontScale}\n" +
                roots.fetchSemanticsNodes().indices.joinToString("\n") { roots[it].printToString() },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun inventoryStates(): MutableMap<Int, InventoryState> = InventoryRepository::class.java
        .getDeclaredField("states").apply { isAccessible = true }
        .get(CanaServices.getInstance().inventory) as MutableMap<Int, InventoryState>

    private fun fixtureApps(): List<AppInfo> = listOf(
        AppInfo("Android System Intelligence", "com.google.android.as", "15.0.42", 42,
            isSystemApp = true, isUninstalled = false, isDisabled = false,
            bloatData = BloatData(InstallData.OEM,
                "Provides smart suggestions and system features.\nReview the impact before changing this app.",
                RemovalRecommendation.ADVANCED, source = "Android Debloat List"), apkSizeBytes = 84_000_000),
        AppInfo("Camera", "com.android.camera2", "15", 15, true, false, false,
            BloatData(InstallData.AOSP, "Camera application", RemovalRecommendation.RECOMMENDED), apkSizeBytes = 26_000_000),
        AppInfo("Digital Wellbeing", "com.google.android.apps.wellbeing", "1.2", 12, true, false, true,
            BloatData(InstallData.OEM, "Device usage insights", RemovalRecommendation.RECOMMENDED), apkSizeBytes = 52_000_000),
        AppInfo("Google Play services", "com.google.android.gms", "25.34.62", 253462, true, false, false,
            BloatData(InstallData.OEM, "Shared Google services", RemovalRecommendation.UNSAFE), apkSizeBytes = 249_000_000),
        AppInfo("Settings", "com.android.settings", "15", 15, true, false, false,
            BloatData(InstallData.AOSP, "Android device settings", RemovalRecommendation.UNSAFE), apkSizeBytes = 64_000_000),
        AppInfo("Optional Weather and Partner Recommendations", "example.optional.weather", "2.0", 20,
            true, true, false, BloatData(InstallData.OEM, "Optional weather recommendations", RemovalRecommendation.RECOMMENDED),
            apkSizeBytes = 14_000_000),
    )
}
