package io.github.samolego.canta.util

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.CancellationException

class DexClassNamesTest {
    private fun uleb(value: Int): ByteArray {
        var rest = value
        val out = ArrayList<Byte>()
        do {
            var byte = rest and 0x7F
            rest = rest ushr 7
            if (rest != 0) byte = byte or 0x80
            out.add(byte.toByte())
        } while (rest != 0)
        return out.toByteArray()
    }

    /** A minimal DEX: [defined] classes get `class_def` entries, [referenced] ones only type ids. */
    private fun dex(defined: List<String>, referenced: List<String> = emptyList(), version: String = "035"): ByteArray {
        val descriptors = (defined + referenced).map { "L" + it.replace('.', '/') + ";" }
        val header = if (version == "041") 0x78 else 0x70
        val stringIdsOff = header
        val typeIdsOff = stringIdsOff + descriptors.size * 4
        val classDefsOff = typeIdsOff + descriptors.size * 4
        val dataOff = classDefsOff + defined.size * 32
        val data = ByteArrayOutputStream()
        val stringOffsets = IntArray(descriptors.size)
        descriptors.forEachIndexed { index, descriptor ->
            stringOffsets[index] = dataOff + data.size()
            data.write(uleb(descriptor.length)); data.write(descriptor.toByteArray(Charsets.UTF_8)); data.write(0)
        }
        val buffer = ByteBuffer.allocate(dataOff + data.size()).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("dex\n$version\u0000".toByteArray(Charsets.US_ASCII))
        buffer.position(0x20); buffer.putInt(buffer.capacity()); buffer.putInt(header); buffer.putInt(0x12345678)
        buffer.position(0x38); buffer.putInt(descriptors.size); buffer.putInt(stringIdsOff); buffer.putInt(descriptors.size); buffer.putInt(typeIdsOff)
        buffer.position(0x60); buffer.putInt(defined.size); buffer.putInt(classDefsOff); buffer.putInt(data.size()); buffer.putInt(dataOff)
        if (version == "041") { buffer.position(0x70); buffer.putInt(buffer.capacity()); buffer.putInt(0) }
        buffer.position(stringIdsOff); stringOffsets.forEach { buffer.putInt(it) }
        buffer.position(typeIdsOff); descriptors.indices.forEach { buffer.putInt(it) }
        buffer.position(classDefsOff); defined.indices.forEach { index -> buffer.putInt(index); repeat(7) { buffer.putInt(0) } }
        buffer.position(dataOff); buffer.put(data.toByteArray())
        return buffer.array()
    }

    @Test fun listsDefinedClassesButNotMerelyReferencedTypes() {
        val names = DexClassNames.fromDex(dex(
            listOf("com.example.app.MainActivity", "com.example.app.MainActivity\$Inner", "ütf.Ünïcode"),
            listOf("com.google.android.gms.analytics.Tracker", "java.lang.Object")))
        assertEquals(listOf("com.example.app.MainActivity", "com.example.app.MainActivity\$Inner", "ütf.Ünïcode"), names)
    }

    @Test fun rejectsForeignBytesTruncationAndUnknownVersions() {
        assertThrows(IllegalArgumentException::class.java) { DexClassNames.fromDex("PK\u0003\u0004 this is not a dex file at all, just filler".toByteArray()) }
        val whole = dex(listOf("a.b.C"))
        assertThrows(IllegalArgumentException::class.java) { DexClassNames.fromDex(whole.copyOf(whole.size - 3)) }
        assertThrows(IllegalArgumentException::class.java) { DexClassNames.fromDex(whole.copyOf(0x40)) }
        assertThrows(IllegalArgumentException::class.java) { DexClassNames.fromDex(dex(listOf("a.b.C"), version = "042")) }
        assertEquals(emptyList<String>(), DexClassNames.fromDex(dex(emptyList())))
    }

    @Test fun readsEveryDexInAMultiDexApkAndIgnoresOtherEntries() {
        val dir = Files.createTempDirectory("dex-test").toFile()
        try {
            val apk = dir.resolve("base.apk")
            ZipOutputStream(apk.outputStream()).use { zip ->
                fun add(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
                add("classes2.dex", dex(listOf("second.dex.Two")))
                add("resources.arsc", ByteArray(16))
                add("assets/classes.dex", dex(listOf("asset.Ignored")))
                add("classes.dex", dex(listOf("first.dex.One", "first.dex.Also")))
            }
            assertEquals(listOf("first.dex.One", "first.dex.Also", "second.dex.Two"), DexClassNames.fromApk(apk))
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsOversizedDexInsteadOfReturningPartialApkClasses() {
        val dir = Files.createTempDirectory("dex-limit").toFile()
        try {
            val apk = dir.resolve("base.apk")
            ZipOutputStream(apk.outputStream()).use { zip ->
                for (name in listOf("classes.dex", "classes2.dex")) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(dex(listOf("a.b.C"))); zip.closeEntry()
                }
            }
            // A corrupt/hostile central directory must not turn a missing scan into "no matches".
            val bytes = apk.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val central = bytes.indices.filter { it + 4 <= bytes.size && buffer.getInt(it) == 0x02014b50 }
            buffer.putInt(central.last() + 24, (DexClassNames.MAX_DEX_BYTES + 1).toInt())
            apk.writeBytes(bytes)
            assertThrows(IllegalArgumentException::class.java) { DexClassNames.fromApk(apk) }
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsEmptyDexAndInvalidHeaderSizes() {
        assertThrows(IllegalArgumentException::class.java) { DexClassNames.fromDex(byteArrayOf()) }
        for (field in listOf(0x20, 0x24)) {
            val bytes = dex(listOf("a.b.C"))
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(field, 0)
            assertThrows(IllegalArgumentException::class.java) { DexClassNames.fromDex(bytes) }
        }
    }

    @Test fun cancellationStopsBeforeProcessingAllClasses() {
        val dir = Files.createTempDirectory("dex-cancel").toFile()
        try {
            val apk = dir.resolve("base.apk")
            ZipOutputStream(apk.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("classes.dex"))
                zip.write(dex((0..1024).map { "a.b.Class$it" })); zip.closeEntry()
            }
            var classes = 0
            assertThrows(CancellationException::class.java) {
                DexClassNames.forEachClass(apk, checkActive = {
                    if (classes > 0) throw CancellationException()
                }) { classes++ }
            }
            assertTrue(classes in 1..256)
        } finally { dir.deleteRecursively() }
    }

    @Test fun readsVersion41ContainerAndRejectsIncorrectContainerBounds() {
        val first = dex(listOf("first.Container"), version = "041")
        val second = dex(listOf("second.Container"), version = "041")
        val total = first.size + second.size
        ByteBuffer.wrap(first).order(ByteOrder.LITTLE_ENDIAN).putInt(0x70, total)
        val buffer = ByteBuffer.wrap(second).order(ByteOrder.LITTLE_ENDIAN)
        for (field in listOf(0x3C, 0x44, 0x64)) buffer.putInt(field, buffer.getInt(field) + first.size)
        buffer.putInt(0x78, buffer.getInt(0x78) + first.size) // string_data offset
        buffer.putInt(0x70, total); buffer.putInt(0x74, first.size)
        val container = first + second
        assertEquals(listOf("first.Container", "second.Container"), DexClassNames.fromDex(container))
        buffer.putInt(0x74, 0)
        assertThrows(IllegalArgumentException::class.java) { DexClassNames.fromDex(first + second) }
    }

    /** Real D8 output: Cana's own debug APK, when a build has produced one. It defines Cana and matches no tracker. */
    @Test fun parsesCanaDebugApkWithoutTrackerMatches() {
        val apk = File("build/outputs/apk/debug").listFiles { file -> file.name.endsWith(".apk") }?.firstOrNull()
        assumeTrue("no debug APK built yet", apk != null)
        val names = DexClassNames.fromApk(apk!!)
        assertTrue(names.size > 1000)
        assertTrue(names.contains("io.github.samolego.canta.MainActivity"))
        assertTrue(names.contains("io.github.samolego.canta.util.DexClassNames"))
        assertTrue(names.all { it.isNotBlank() && !it.contains('/') && !it.endsWith(";") })
        val signatures = TrackerSignatures.parse(JSONObject(File("src/main/assets/exodus-trackers.json").readText()))
        assertEquals(emptyList<String>(), TrackerMatcher(signatures).scan(names))
    }
}
