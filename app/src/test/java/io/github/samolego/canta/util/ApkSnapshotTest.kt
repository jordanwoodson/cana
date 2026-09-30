package io.github.samolego.canta.util

import io.github.samolego.canta.util.apps.ApkSnapshot
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ApkSnapshotTest {
    @Test fun allSplitsAreRequiredAndIdentityChangesWithFiles() {
        val dir = Files.createTempDirectory("apk-snapshot").toFile()
        try {
            val base = dir.resolve("base.apk").apply { writeText("base") }
            val split = dir.resolve("split.apk").apply { writeText("split") }
            val first = ApkSnapshot.read(base.path, listOf(split.path))!!
            assertEquals(9, first.sizeBytes)
            assertEquals(first, ApkSnapshot.read(base.path, listOf(split.path, base.path, split.path)))
            split.appendText("updated")
            val updated = ApkSnapshot.read(base.path, listOf(split.path))!!
            assertNotEquals(first.identity, updated.identity)
            val changedTime = split.lastModified() + 10000
            assertTrue(split.setLastModified(changedTime))
            assertNotEquals(updated.identity, ApkSnapshot.read(base.path, listOf(split.path))!!.identity)
            assertNotEquals(first.identity, ApkSnapshot.read(base.path, emptyList())!!.identity)
            assertTrue(split.delete())
            assertNull(ApkSnapshot.read(base.path, listOf(split.path)))
            assertNull(ApkSnapshot.read(null, listOf(base.path)))
        } finally { dir.deleteRecursively() }
    }
}
