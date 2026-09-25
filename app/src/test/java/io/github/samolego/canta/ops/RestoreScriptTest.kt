package io.github.samolego.canta.ops

import io.github.samolego.canta.data.proto.OperationRecord
import org.junit.Assert.*
import org.junit.Test

class RestoreScriptTest {
    private fun record(action: String, state: String, user: Int = 10, name: String = "test.app") =
        OperationRecord.newBuilder().setId(action).setPackageName(name).setUserId(user)
            .setAction(action).setPreviousState(state).setAfterState("{}").setCompleted(true)
            .setSuccess(true).setChanged(true).build()

    @Test fun shellArgumentsAreLiteralIncludingQuotesAndSubstitutions() {
        val value = "a'b;\$(printf injected)\nline"
        val process = ProcessBuilder("sh", "-c", "printf '%s' " + RestoreScript.quote(value)).start()
        assertEquals(value, process.inputStream.bufferedReader().readText())
        assertEquals(0, process.waitFor())
    }

    @Test fun reverseHistoryIncludesPartialChangesAndPendingButSkipsUnchangedFailures() {
        val first = record("uninstall", "{\"installed\":true}")
        val partial = record("disable", "{\"enabledSetting\":0}").toBuilder().setSuccess(false).build()
        val noChange = record("suspend", "{\"suspended\":false}").toBuilder().setChanged(false).setSuccess(false).build()
        val script = RestoreScript.generate(listOf(first, partial, noChange))
        assertTrue(script.indexOf("'default-state'") < script.indexOf("'install-existing'"))
        assertTrue(script.contains("'--user' '10' 'test.app'"))
        assertFalse(script.contains("'unsuspend'"))
        val pending = first.toBuilder().setCompleted(false).setChanged(false).build()
        assertTrue(RestoreScript.generate(listOf(pending)).contains("'install-existing'"))
    }

    @Test fun preservesComponentAndAppEnabledStatesInsteadOfAlwaysEnabling() {
        val plan = RestoreScript.plan(record("component", "{\"component\":\"test.app/.Tracker\",\"enabledSetting\":2}"))
        assertEquals(listOf("pm", "disable", "--user", "10", "test.app/.Tracker"), plan.commands.single())
        assertEquals("enable", RestoreScript.plan(record("disable", "{\"enabledSetting\":1}")).commands.single()[1])
    }

    @Test fun restoresRecordedBackgroundMeteredAndPermissionValues() {
        val background = RestoreScript.plan(record("background", "{\"runAnyInBackground\":\"foreground\",\"standbyBucket\":20}"))
        assertEquals(listOf("cmd", "appops", "set", "--user", "10", "test.app", "RUN_ANY_IN_BACKGROUND", "foreground"), background.commands.first())
        assertEquals(listOf("am", "set-standby-bucket", "--user", "10", "test.app", "20"), background.commands.last())
        val metered = RestoreScript.plan(record("metered", "{\"appId\":10083,\"meteredPolicy\":4}"))
        assertTrue(metered.commands.contains(listOf("cmd", "netpolicy", "add", "restrict-background-whitelist", "1010083")))
        assertFalse(metered.commands.any { it.last() == "10083" })
        val permissions = RestoreScript.plan(record("permissions", "{\"permissions\":[{\"name\":\"android.permission.CAMERA\",\"granted\":true,\"flags\":1}]}"))
        assertTrue(permissions.commands.any { it.take(2) == listOf("pm", "grant") })
        assertTrue(permissions.commands.any { it.last() == "user-set" })
        assertFalse(permissions.commands.any { it.take(2) == listOf("pm", "set-permission-flags") && it.last() == "user-fixed" })
    }

    @Test fun unknownStateAndIrrecoverableUpdateAreExplicitManualSteps() {
        val reset = record("remove_updates", "{\"installed\":false,\"updatedSystemApp\":true}")
            .toBuilder().setAfterState("{\"installed\":false,\"updatedSystemApp\":false}").build()
        val plan = RestoreScript.plan(reset)
        assertTrue(plan.commands.isEmpty())
        assertTrue(plan.notes.any { it.contains("APK") })
        assertTrue(RestoreScript.plan(record("disable", "{}")).notes.isNotEmpty())
        assertTrue(RestoreScript.generate(listOf(reset)).contains("manual_steps=1"))
    }

    @Test fun restoresAbsentGlobalSettingWithDeleteAndNetworkWithPreviousBoolean() {
        val system = RestoreScript.plan(record("system", "{\"settings\":{\"private_dns_mode\":null,\"wifi_scan_always_enabled\":\"1\"}}"))
        assertTrue(system.commands.contains(listOf("settings", "delete", "global", "private_dns_mode")))
        assertTrue(system.commands.contains(listOf("settings", "put", "global", "wifi_scan_always_enabled", "1")))
        val network = RestoreScript.plan(record("network", "{\"networkEnabled\":true}", user = 0))
        assertTrue(network.commands.flatten().contains("true"))
    }
}
