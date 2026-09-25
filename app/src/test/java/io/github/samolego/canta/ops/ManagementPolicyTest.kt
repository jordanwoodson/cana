package io.github.samolego.canta.ops

import io.github.samolego.canta.data.proto.OperationRecord
import org.junit.Assert.*
import org.junit.Test

class ManagementPolicyTest {
    private fun record(id: String, batch: String, action: String = "disable", user: Int = 10,
        pkg: String = "test.app", after: String = "{}") = OperationRecord.newBuilder()
        .setId(id).setBatchId(batch).setAction(action).setUserId(user).setPackageName(pkg)
        .setPreviousState("{\"installed\":true}").setAfterState(after).setCompleted(true).setChanged(true).build()

    @Test fun lastBatchReversesPartialChangesAndSkipsAutomationAndUnchanged() {
        val first = record("a", "one")
        val partial = record("b", "one").toBuilder().setSuccess(false).build()
        val unchanged = record("c", "two").toBuilder().setChanged(false).build()
        val automatic = record("d", "three", "network_reapply")
        assertEquals(listOf(partial, first), ManagementPolicy.lastBatch(listOf(first, partial, unchanged, automatic)))
    }
    @Test fun successfulUndoIsExcludedButFailedUndoLeavesOriginalRetryable() {
        val a = record("a", "one")
        val b = record("b", "one")
        val undoA = record("ua", "undo").toBuilder().setUndoOf("a").setSuccess(true).build()
        val undoB = record("ub", "undo").toBuilder().setUndoOf("b").setSuccess(false).build()
        assertEquals(listOf(b), ManagementPolicy.lastBatch(listOf(a, b, undoA, undoB)))
    }
    @Test fun pendingIntentWithPriorStateRemainsRecoverable() {
        val pending = record("p", "pending").toBuilder().setCompleted(false).setChanged(false).build()
        assertEquals(listOf(pending), ManagementPolicy.lastBatch(listOf(pending)))
    }
    @Test fun otaFindsReturnedRemovalAndNewSystemOnlyInKnownProfiles() {
        val before = listOf(PackageInventory(0, setOf("old.app"), setOf("old.app")), PackageInventory(10, emptySet(), setOf("old.app")))
        val after = listOf(PackageInventory(0, setOf("old.app"), setOf("old.app")),
            PackageInventory(10, setOf("old.app", "new.system", "new.user"), setOf("old.app", "new.system")),
            PackageInventory(11, setOf("fresh.profile"), setOf("fresh.profile")))
        val records = listOf(record("r", "batch", "uninstall", after = "{\"installed\":false}", pkg = "old.app"))
        assertEquals(setOf(OtaChange(10, "old.app", true), OtaChange(10, "new.system", false)),
            ManagementPolicy.otaChanges("old", "new", before, after, records).toSet())
        assertTrue(ManagementPolicy.otaChanges("new", "new", before, after, records).isEmpty())
        assertTrue(ManagementPolicy.otaChanges("", "new", before, after, records).isEmpty())
    }
    @Test fun laterReinstallCancelsRemovalIntentAndFailedUnchangedRemovalDoesNotCreateOne() {
        val inventory = listOf(PackageInventory(10, setOf("test.app"), setOf("test.app")))
        val removed = record("r", "b", "uninstall", after = "{\"installed\":false}")
        val restored = record("u", "undo", "uninstall", after = "{\"installed\":true}").toBuilder().setUndoOf("r").build()
        assertTrue(ManagementPolicy.otaChanges("a", "b", inventory, inventory, listOf(removed, restored)).isEmpty())
        val failed = removed.toBuilder().setChanged(false).setAfterState("{\"installed\":true}").build()
        assertTrue(ManagementPolicy.otaChanges("a", "b", inventory, inventory, listOf(failed)).isEmpty())
    }
    @Test fun unusedFilterDistinguishesUnavailableAndNewInstallsFromNeverUsed() {
        val day = 86_400_000L
        val now = 200 * day
        assertFalse(ManagementPolicy.unused90Days(null, day, now))
        assertFalse(ManagementPolicy.unused90Days(0, 180 * day, now))
        assertFalse(ManagementPolicy.unused90Days(150 * day, day, now))
        assertTrue(ManagementPolicy.unused90Days(0, day, now))
        assertTrue(ManagementPolicy.unused90Days(110 * day, day, now))
    }
    @Test fun failedRepeatRemovalDoesNotEraseAnEarlierRemovalIntent() {
        val inventory = listOf(PackageInventory(10, setOf("test.app"), setOf("test.app")))
        val removed = record("r", "b", "uninstall", after = "{\"installed\":false}")
        val repeat = removed.toBuilder().setId("again").setChanged(false).setSuccess(false).build()
        assertEquals(listOf(OtaChange(10, "test.app", true)), ManagementPolicy.otaChanges("a", "b", inventory, inventory, listOf(removed, repeat)))
    }
}
