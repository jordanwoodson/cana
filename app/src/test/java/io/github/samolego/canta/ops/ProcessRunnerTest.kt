package io.github.samolego.canta.ops

import org.junit.Assert.*
import org.junit.Test

class ProcessRunnerTest {
    @Test fun capturesExitCodeAndBothStreams() {
        val result = ProcessRunner.exec(listOf("/bin/sh", "-c", "printf output; printf problem >&2; exit 7"), 2_000)
        assertEquals(7, result.exitCode)
        assertEquals("output", result.stdout)
        assertEquals("problem", result.stderr)
    }

    @Test fun argumentsArePassedLiterallyWithoutShellExpansion() {
        val result = ProcessRunner.exec(listOf("/bin/echo", "hello; exit 7", "$(id)"), 2_000)
        assertEquals(0, result.exitCode)
        assertEquals("hello; exit 7 $(id)\n", result.stdout)
    }

    @Test fun timeoutCannotReportSuccess() {
        val start = System.nanoTime()
        val result = ProcessRunner.exec(listOf("/bin/sleep", "3"), 50)
        assertEquals(124, result.exitCode)
        assertTrue(result.stderr.contains("timed out"))
        assertTrue(TimeUnitMillis(System.nanoTime() - start) < 2_000)
    }

    @Test fun outputLimitFailsExplicitlyAndDoesNotDeadlock() {
        val result = ProcessRunner.exec(listOf("/bin/sh", "-c", "head -c 100000 /dev/zero"), 2_000)
        assertNotEquals(0, result.exitCode)
        assertTrue(result.stdout.length <= 65_536)
        assertTrue(result.stderr.contains("output limit"))
    }

    @Test fun missingExecutableIsAFailure() {
        val result = ProcessRunner.exec(listOf("/does/not/exist"), 2_000)
        assertNotEquals(0, result.exitCode)
        assertTrue(result.stderr.isNotBlank())
    }

    private fun TimeUnitMillis(nanos: Long) = nanos / 1_000_000
}
