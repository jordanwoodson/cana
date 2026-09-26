package io.github.samolego.canta.ops

import io.github.samolego.canta.data.proto.OperationRecord
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class RecoveryOwnerTest {
    @Test fun exportedRecoveryUsesCanaOwnerSeparatelyFromTargetProfile() {
        val apk = Files.createTempFile("cana-owner", ".apk").toFile()
        try {
            val record = OperationRecord.newBuilder().setId("network").setAction("network").setPackageName("test.app")
                .setUserId(11).setPreviousState("{\"ownerUserId\":10,\"appId\":10123,\"networkRule\":0}")
                .setChanged(true).setCompleted(true).build()
            val wrappers = "pm() { printf 'path-owner=%s\\n' \"\$3\" >&2; printf '%s\\n' ${RestoreScript.quote("package:${apk.absolutePath}")}; }; " +
                "app_process() { printf 'adapter=%s\\n' \"\$*\"; };\n"
            val process = ProcessBuilder("sh", "-c", wrappers + RestoreScript.generate(listOf(record))).start()
            val output = process.inputStream.bufferedReader().readText()
            val errors = process.errorStream.bufferedReader().readText()
            assertEquals(output + errors, 0, process.waitFor())
            assertTrue(errors, errors.contains("path-owner=10"))
            assertTrue(output, output.contains("--owner-user 10 network-set test.app 11 10123 0"))
        } finally { apk.delete() }
    }
}
