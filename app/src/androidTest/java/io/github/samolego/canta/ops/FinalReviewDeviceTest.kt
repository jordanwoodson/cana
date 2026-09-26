package io.github.samolego.canta.ops

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.data.SettingsStore
import io.github.samolego.canta.util.CantaPresetData
import io.github.samolego.canta.util.withPackageAuthentication
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import rikka.shizuku.Shizuku

class FinalReviewDeviceTest {
    private val services get() = CanaServices.getInstance()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val pkg = "io.github.jordanwoodson.cana.fixture"
    private suspend fun ready() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
    }
    private fun adb(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }

    @Test fun reinstallUndoPreservesRetainedDataInBothProfiles() = runBlocking {
        ready()
        val ops = services.packageOps
        for (user in listOf(0, 10)) {
            assertTrue(ops.reinstall(pkg, user).success)
            adb("run-as $pkg --user $user mkdir -p files")
            adb("run-as $pkg --user $user touch files/review-retained-$user")
        }
        try {
            for (user in listOf(0, 10)) {
                assertTrue(ops.uninstall(pkg, user, keepData = true).success)
                assertTrue(ops.reinstall(pkg, user).success)
                val record = services.history.records.first().last()
                assertEquals("reinstall", record.action)
                val undone = ops.undo(record)
                assertTrue(undone.message, undone.success)
                assertTrue(ops.reinstall(pkg, user).success)
                for (checkUser in listOf(0, 10)) {
                    assertTrue(adb("run-as $pkg --user $checkUser ls files").contains("review-retained-$checkUser"))
                }
            }
        } finally { for (user in listOf(0, 10)) ops.reinstall(pkg, user) }
    }

    @Test fun manualRecoveryCannotHideEarlierReversibleBatches() = runBlocking {
        ready()
        val ops = services.packageOps
        val system = "com.android.printspooler"
        try {
            assertTrue(ops.reinstall(system, 0).success)
            assertTrue(ops.reinstall(system, 10).success)
            assertTrue(services.shell.exec(listOf("pm", "install", "-r", "/data/local/tmp/cana-printspooler.apk"), 30_000).success)
            assertTrue(ops.uninstall(system, 10, keepData = true).success)
            assertTrue(ops.setEnabled(pkg, 10, false).success)
            val disabled = services.history.records.first().last()
            assertTrue(ops.removeUpdates(system, 10, setOf(0)).success)
            val cleanup = services.history.records.first().last()
            assertEquals("remove_updates", services.undo.lastBatch().first().action)
            val manual = ops.undo(cleanup)
            assertFalse(manual.success)
            assertTrue(manual.recoveryComplete)
            assertEquals(disabled.id, services.undo.lastBatch().first().id)
            assertTrue(ops.undo(disabled).success)
            assertTrue(ops.undo(cleanup).skipped)
            assertTrue(ops.reinstall(system, 10).success)
            assertTrue(ops.uninstall(system, 10).success)
            val removal = services.history.records.first().last()
            val recovered = ops.undo(removal)
            assertFalse(recovered.success) // Deleted data still has a visible manual obligation.
            assertTrue(recovered.recoveryComplete)
            assertTrue(ops.undo(removal).skipped)
            assertTrue(services.undo.lastBatch().none { it.id == removal.id })
        } finally {
            ops.setEnabled(pkg, 10, true)
            ops.removeUpdates(system, 10, setOf(0))
            ops.reinstall(system, 0); ops.reinstall(system, 10)
        }
    }

    @Test fun durableAuthenticationSettingGuardsCapturedPresetOtaAndUndoActions() = runBlocking {
        ready()
        val settings = SettingsStore.getInstance()
        val original = settings.authEnabledFlow.first()
        val ops = services.packageOps
        try {
            assertTrue(ops.reinstall(pkg, 10).success)
            assertTrue(ops.setEnabled(pkg, 10, false).success)
            val disabled = services.history.records.first().last()
            val preset = CantaPresetData("Authentication regression", "", 1, setOf(pkg))
            val actions: List<suspend () -> Unit> = listOf(
                { services.presets.apply(preset, listOf(10), emptyMap()); Unit },
                { ops.uninstall(pkg, 10); Unit },
                { services.undo.undo(listOf(disabled)); Unit },
            )
            settings.setAuthEnabled(true)
            val history = services.history.records.first()
            var prompts = 0
            for (action in actions) withPackageAuthentication(context, { prompts++; false }, action)
            assertEquals(3, prompts)
            assertEquals(history, services.history.records.first())
            // A positive authentication result permits exactly the captured undo.
            withPackageAuthentication(context, { true }, actions.last())
            assertTrue(ops.getPackageInfo(pkg, 10)!!.applicationInfo!!.enabled)
            settings.setAuthEnabled(false)
            withPackageAuthentication(context, { error("Must not prompt") }) { ops.setEnabled(pkg, 10, false) }
            assertFalse(ops.getPackageInfo(pkg, 10)!!.applicationInfo!!.enabled)
        } finally {
            settings.setAuthEnabled(original)
            ops.reinstall(pkg, 10); ops.setEnabled(pkg, 10, true)
        }
    }
}
