package io.github.samolego.canta.ops

import android.content.pm.ApplicationInfo
import android.os.Build
import io.github.samolego.canta.util.apps.UserProfile
import io.github.samolego.canta.util.shizuku.ShizukuUserUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Requires an isolated emulator with Work and Clone profiles and the disposable fixture APK. */
class ProfileIsolationDeviceTest {
    @Test fun parentRemovalAndUndoRefuseAndroidCascadeButChildRemovalStaysIsolated() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val services = CanaServices.getInstance()
        val ops = services.packageOps
        val clone = ShizukuUserUtils.getUsers().single { it.kind == UserProfile.Kind.CLONE && it.parentId == 0 }.id
        val name = "io.github.jordanwoodson.cana.fixture"
        fun installed(user: Int) = ops.getPackageInfo(name, user)?.applicationInfo?.let {
            it.flags and ApplicationInfo.FLAG_INSTALLED != 0
        } == true
        try {
            assertTrue(ops.reinstall(name, 0).success)
            assertTrue(ops.reinstall(name, 10).success)
            assertTrue(ops.reinstall(name, clone).success)
            for (keepData in listOf(false, true)) {
                val blocked = ops.uninstall(name, 0, keepData = keepData)
                assertFalse(blocked.message, blocked.success)
                assertFalse(blocked.changed)
                assertTrue(blocked.message, blocked.message.contains("also remove"))
                assertTrue(installed(0) && installed(10) && installed(clone))
            }
            assertTrue(ops.uninstall(name, clone, keepData = true).success)
            assertFalse(installed(clone))
            assertTrue(installed(0) && installed(10))
            assertTrue(ops.uninstall(name, 0, keepData = true).success)
            assertTrue(ops.reinstall(name, 0).success)
            val restore = services.history.records.first().last { it.packageName == name }
            assertTrue(ops.reinstall(name, clone).success)
            val undo = ops.undo(restore)
            assertFalse(undo.message, undo.success)
            assertFalse(undo.changed)
            assertTrue(installed(0) && installed(clone) && installed(10))
        } finally {
            ops.reinstall(name, 0)
            ops.reinstall(name, 10)
            ops.uninstall(name, clone, keepData = true)
        }
    }

    @Test fun realWorkInventoryHasProfileSpecificGuidanceWithoutChangingPersonalRatings() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val inventory = CanaServices.getInstance().inventory
        val work = inventory.snapshot(10)
        assertNull(work.error)
        assertTrue(work.apps.isNotEmpty())
        assertTrue(work.apps.all { it.profileGuidance != null })
        val personal = inventory.snapshot(0)
        assertNull(personal.error)
        assertTrue(personal.apps.isNotEmpty())
        assertTrue(personal.apps.all { it.profileGuidance == null })
    }
}
