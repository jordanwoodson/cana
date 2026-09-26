package io.github.samolego.canta.ops

import org.junit.Assert.*
import org.junit.Test

class RecoveryAuditTest {
    @Test fun reorderedSnapshotFieldsDoNotCreateFalseRecoveryObligation() {
        val result = packageOutcome("{\"installed\":true,\"nested\":{\"a\":1,\"b\":null}}",
            "{\"nested\":{\"b\":null,\"a\":1},\"installed\":true}", OperationResult(true, "OK"), "Unknown")
        assertFalse(result.changed)
    }

    @Test fun reviewedLauncherCannotAuthorizeReplacementLauncher() {
        val reviewed = SafetyPolicy.assess("launcher.old", SafetySnapshot(roles = mapOf("HOME" to setOf("launcher.old"))), emptyList())
        val replacement = SafetyPolicy.assess("launcher.new", SafetySnapshot(roles = mapOf("HOME" to setOf("launcher.new"))), emptyList())
        assertFalse(replacement.permits(reviewed.warnings.map { it.key }.toSet()))
    }

    @Test fun approvalsCannotCrossProfiles() {
        val state = SafetySnapshot(roles = mapOf("HOME" to setOf("launcher")))
        val personal = SafetyPolicy.assess("launcher", state, emptyList(), 0)
        val work = SafetyPolicy.assess("launcher", state, emptyList(), 10)
        assertFalse(work.permits(personal.warnings.map { it.key }.toSet()))
    }

    @Test fun unknownSnapshotSchemaCannotGenerateMutationCommands() {
        val record = io.github.samolego.canta.data.proto.OperationRecord.newBuilder().setAction("disable")
            .setPackageName("test.app").setUserId(10).setPreviousState("{\"snapshotSchema\":2,\"enabledSetting\":0}").build()
        val plan = RestoreScript.plan(record)
        assertTrue(plan.commands.isEmpty())
        assertTrue(plan.notes.isNotEmpty())
    }

    @Test fun legacySchemaComparesWithCurrentWithoutLosingExplicitNulls() {
        assertTrue(SnapshotState.equal("{\"settings\":{\"dns\":null}}", "{\"snapshotSchema\":1,\"settings\":{\"dns\":null}}"))
        assertFalse(SnapshotState.equal("{\"settings\":{\"dns\":null}}", "{\"settings\":{}}"))
    }
}
