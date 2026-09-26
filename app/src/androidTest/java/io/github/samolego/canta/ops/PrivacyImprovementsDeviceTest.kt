package io.github.samolego.canta.ops

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import rikka.shizuku.Shizuku

/** Mutating acceptance coverage is restricted to the explicitly provisioned emulator fixture. */
class PrivacyImprovementsDeviceTest {
    private val services get() = CanaServices.getInstance()
    private val pkg = "io.github.jordanwoodson.cana.fixture"
    @Before fun emulatorOnly() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
    }
    @Test fun selectedPermissionLeavesOtherPermissionsUntouchedAndUndoRestoresExactSelection() = runBlocking {
        val before = services.privacy.snapshot(pkg, 10, PrivacyAction.PERMISSIONS)
        val entries = before.getJSONArray("permissions")
        val selected = (0 until entries.length()).map { entries.getJSONObject(it) }
            .first { PrivacyPolicy.mutablePermission(it.getInt("flags")) }.getString("name")
        val result = services.privacy.restrict(pkg, 10, PrivacyAction.PERMISSIONS, selectedPermissions = setOf(selected))
        val record = services.history.records.first().last { it.packageName == pkg && it.action == "permissions" && it.undoOf.isEmpty() }
        try {
            assertTrue(result.message, result.success)
            val after = services.privacy.snapshot(pkg, 10, PrivacyAction.PERMISSIONS).getJSONArray("permissions")
            for (index in 0 until entries.length()) if (entries.getJSONObject(index).getString("name") != selected)
                assertEquals(entries.getJSONObject(index).toString(), after.getJSONObject(index).toString())
            val undo = services.privacy.undo(record)
            assertTrue(undo.message, undo.success)
            assertEquals(before.toString(), services.privacy.snapshot(pkg, 10, PrivacyAction.PERMISSIONS).toString())
        } finally { services.privacy.undo(record) }
    }
    @Test fun forgettingMissingPackageNeedsNoInstalledUidAndPreservesPeerIntent() = runBlocking {
        val missing = "io.github.jordanwoodson.cana.nonexistentfixture"
        services.privacy.initialize()
        services.desiredPrivacy.setBlock(missing, 10, 10199, true)
        services.desiredPrivacy.setMetered(missing, 0, 10199, true)
        try {
            val result = services.privacy.forgetDesired(missing, 10, PrivacyAction.NETWORK)
            assertTrue(result.message, result.success)
            assertFalse(services.desiredPrivacy.blocks.first().any { it.packageName == missing && it.userId == 10 })
            assertTrue(services.desiredPrivacy.meteredBlocks.first().any { it.packageName == missing && it.userId == 0 })
        } finally { services.desiredPrivacy.forget(missing, 10, false); services.desiredPrivacy.forget(missing, 0, true) }
    }
    @Test fun legacySavedRuleRequiresReviewWithoutApplyingItsLiveUid() = runBlocking {
        val before = services.privacy.snapshot(pkg, 10, PrivacyAction.NETWORK)
        assertFalse("Fixture must start without a saved rule", services.desiredPrivacy.blocks.first().any { it.packageName == pkg && it.userId == 10 })
        services.desiredPrivacy.setBlock(pkg, 10, before.getInt("appId"), true)
        try {
            services.privacy.reapplyDesired()
            val entry = services.desiredPrivacy.blocks.first().single { it.packageName == pkg && it.userId == 10 }
            assertEquals("needs_review", entry.status)
            val after = services.privacy.snapshot(pkg, 10, PrivacyAction.NETWORK)
            assertEquals(before.getInt("networkRule"), after.getInt("networkRule"))
            assertEquals(before.getBoolean("chainEnabled"), after.getBoolean("chainEnabled"))
        } finally { services.privacy.forgetDesired(pkg, 10, PrivacyAction.NETWORK) }
    }
    @Test fun adapterReportsStableIdentityForSelectedProfile() = runBlocking {
        val owner = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.uid / 100_000
        val command = listOf("cana-privacy", "--owner-user", "$owner", "identity-get", pkg, "10", "-")
        val first = services.shell.exec(command)
        val second = services.shell.exec(command)
        assertTrue(first.message, first.success)
        assertTrue(second.message, second.success)
        assertEquals(first.stdout, second.stdout)
        assertTrue(first.stdout.contains(pkg))
    }
}
