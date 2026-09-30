package io.github.samolego.canta.util

import java.io.File
import java.util.zip.ZipFile

/**
 * Lists the classes an APK defines, the same input εxodus derives tracker matches from. Only
 * `class_def` entries count: a type that is merely referenced (a Play Services class the app calls,
 * say) is not part of the app's own code and must not produce a match. Pure byte parsing, so it runs
 * for any profile's APK without Shizuku and is unit-testable on the JVM.
 */
object DexClassNames {
    private val DEX_ENTRY = Regex("""classes\d*\.dex""")
    private const val HEADER_SIZE = 0x70
    private const val ENDIAN_CONSTANT = 0x12345678
    private const val MAX_ITEMS = 1 shl 20
    private const val MAX_DESCRIPTOR = 4096
    /** Reject scans beyond the memory/work budget; never present a partial scan as clean. */
    const val MAX_DEX_BYTES = 64L * 1024 * 1024
    private const val MAX_APK_DEX_BYTES = 256L * 1024 * 1024
    private const val MAX_DEX_ENTRIES = 256

    fun fromApk(apk: File): List<String> = buildList { forEachClass(apk, onClass = { add(it) }) }

    /** Streams names to the matcher, retaining only one bounded DEX byte array at a time. */
    fun forEachClass(apk: File, checkActive: () -> Unit = {}, onClass: (String) -> Unit) = ZipFile(apk).use { zip ->
        var totalBytes = 0L
        var entries = 0
        val dexEntries = zip.entries().asSequence().filter { checkActive(); DEX_ENTRY.matches(it.name) }
            .take(MAX_DEX_ENTRIES + 1).sortedBy { it.name }
        for (entry in dexEntries) {
            checkActive()
            require(++entries <= MAX_DEX_ENTRIES) { "Too many DEX files in ${apk.name}" }
            require(entry.size in 1..MAX_DEX_BYTES) { "${entry.name} is empty or exceeds the 64 MiB scan limit" }
            totalBytes += entry.size
            require(totalBytes <= MAX_APK_DEX_BYTES) { "APK exceeds the 256 MiB DEX scan limit" }
            val bytes = ByteArray(entry.size.toInt())
            zip.getInputStream(entry).use { input ->
                var offset = 0
                while (offset < bytes.size) {
                    checkActive()
                    val read = input.read(bytes, offset, minOf(64 * 1024, bytes.size - offset))
                    require(read > 0) { "Truncated ${entry.name}" }
                    offset += read
                }
                require(input.read() == -1) { "${entry.name} exceeds its declared size" }
            }
            parseDex(bytes, checkActive, onClass)
        }
    }

    /** Parses one DEX file (versions 035 to 041); a version 041 container yields every embedded file. */
    fun fromDex(bytes: ByteArray): List<String> = buildList { parseDex(bytes, {}, { add(it) }) }

    private fun parseDex(bytes: ByteArray, checkActive: () -> Unit, onClass: (String) -> Unit) {
        require(bytes.isNotEmpty()) { "Empty DEX file" }
        var base = 0
        while (base < bytes.size) {
            checkActive()
            require(bytes.size - base >= HEADER_SIZE) { "Truncated DEX header" }
            require(bytes[base] == 'd'.code.toByte() && bytes[base + 1] == 'e'.code.toByte() &&
                bytes[base + 2] == 'x'.code.toByte() && bytes[base + 3] == '\n'.code.toByte() && bytes[base + 7] == 0.toByte()) { "Not a DEX file" }
            val version = String(bytes, base + 4, 3, Charsets.US_ASCII).toIntOrNull() ?: throw IllegalArgumentException("Unknown DEX version")
            require(version in setOf(35, 37, 38, 39, 40, 41)) { "Unsupported DEX version $version" }
            require(u4(bytes, base + 0x28) == ENDIAN_CONSTANT) { "Big-endian DEX is not supported" }
            val fileSize = u4(bytes, base + 0x20)
            val headerSize = if (version == 41) 0x78 else HEADER_SIZE
            require(u4(bytes, base + 0x24) == headerSize && fileSize >= headerSize && fileSize <= bytes.size - base) { "Invalid DEX header or file size" }
            if (version == 41) {
                require(u4(bytes, base + 0x70) == bytes.size && u4(bytes, base + 0x74) == base) { "Invalid DEX container header" }
            } else require(base == 0 && fileSize == bytes.size) { "Invalid DEX file size" }
            // Version 041 offsets are relative to the physical container, not this header.
            val origin = if (version >= 41) 0 else base
            val stringIdsSize = u4(bytes, base + 0x38)
            val stringIdsOff = origin + u4(bytes, base + 0x3C)
            val typeIdsSize = u4(bytes, base + 0x40)
            val typeIdsOff = origin + u4(bytes, base + 0x44)
            val classDefsSize = u4(bytes, base + 0x60)
            val classDefsOff = origin + u4(bytes, base + 0x64)
            require(classDefsSize <= MAX_ITEMS && stringIdsSize <= MAX_ITEMS && typeIdsSize <= MAX_ITEMS) { "Implausible DEX table sizes" }
            checkRange(bytes, stringIdsOff, stringIdsSize * 4L)
            checkRange(bytes, typeIdsOff, typeIdsSize * 4L)
            checkRange(bytes, classDefsOff, classDefsSize * 32L)
            for (i in 0 until classDefsSize) {
                if (i % 256 == 0) checkActive()
                val classIdx = u4(bytes, classDefsOff + i * 32)
                require(classIdx < typeIdsSize) { "class_idx out of range" }
                val descriptorIdx = u4(bytes, typeIdsOff + classIdx * 4)
                require(descriptorIdx < stringIdsSize) { "descriptor_idx out of range" }
                val descriptor = string(bytes, origin + u4(bytes, stringIdsOff + descriptorIdx * 4))
                if (descriptor.length > 2 && descriptor[0] == 'L' && descriptor.last() == ';') {
                    onClass(descriptor.substring(1, descriptor.length - 1).replace('/', '.'))
                }
            }
            if (version < 41) break
            base += fileSize
        }
    }

    private fun u4(bytes: ByteArray, offset: Int): Int {
        checkRange(bytes, offset, 4)
        val value = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or ((bytes[offset + 3].toInt() and 0xFF) shl 24)
        require(value >= 0) { "DEX offset or size exceeds 2 GB" }
        return value
    }

    private fun checkRange(bytes: ByteArray, offset: Int, length: Long) {
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) { "Offset $offset outside DEX" }
    }

    private fun at(bytes: ByteArray, index: Int): Int {
        require(index in bytes.indices) { "Offset $index outside DEX" }
        return bytes[index].toInt() and 0xFF
    }

    /** A `string_data_item`: uleb128 UTF-16 length, then MUTF-8 bytes ended by a zero byte. */
    private fun string(bytes: ByteArray, offset: Int): String {
        var position = offset
        var shift = 0
        var utf16Length = 0
        while (true) {
            val byte = at(bytes, position++)
            utf16Length = utf16Length or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
            require(shift <= 28) { "Bad uleb128 length" }
        }
        require(utf16Length in 0..MAX_DESCRIPTOR) { "Implausible descriptor length" }
        val text = StringBuilder(utf16Length)
        while (true) {
            val a = at(bytes, position++)
            when {
                a == 0 -> return text.toString()
                a < 0x80 -> text.append(a.toChar())
                a and 0xE0 == 0xC0 -> {
                    val b = at(bytes, position++)
                    text.append((((a and 0x1F) shl 6) or (b and 0x3F)).toChar())
                }
                a and 0xF0 == 0xE0 -> {
                    val b = at(bytes, position++)
                    val c = at(bytes, position++)
                    text.append((((a and 0x0F) shl 12) or ((b and 0x3F) shl 6) or (c and 0x3F)).toChar())
                }
                else -> throw IllegalArgumentException("Bad MUTF-8 sequence")
            }
            require(text.length <= MAX_DESCRIPTOR) { "Unterminated descriptor" }
        }
    }
}
