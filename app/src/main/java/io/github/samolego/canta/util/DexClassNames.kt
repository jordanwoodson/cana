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
    /** Larger entries are skipped rather than loaded into memory on a phone. */
    const val MAX_DEX_BYTES = 192L * 1024 * 1024

    /** Class names from every `classes*.dex` in [apk]; [onSkipped] reports oversized entries. */
    fun fromApk(apk: File, onSkipped: (String) -> Unit = {}): List<String> = ZipFile(apk).use { zip ->
        buildList {
            for (entry in zip.entries().asSequence().filter { DEX_ENTRY.matches(it.name) }.sortedBy { it.name }) {
                if (entry.size > MAX_DEX_BYTES) { onSkipped(entry.name); continue }
                addAll(fromDex(zip.getInputStream(entry).use { it.readBytes() }))
            }
        }
    }

    /** Parses one DEX file (versions 035 to 041); a version 041 container yields every embedded file. */
    fun fromDex(bytes: ByteArray): List<String> {
        val names = ArrayList<String>()
        var base = 0
        while (base < bytes.size) {
            require(bytes.size - base >= HEADER_SIZE) { "Truncated DEX header" }
            require(bytes[base] == 'd'.code.toByte() && bytes[base + 1] == 'e'.code.toByte() &&
                bytes[base + 2] == 'x'.code.toByte() && bytes[base + 3] == '\n'.code.toByte() && bytes[base + 7] == 0.toByte()) { "Not a DEX file" }
            val version = String(bytes, base + 4, 3, Charsets.US_ASCII).toIntOrNull() ?: throw IllegalArgumentException("Unknown DEX version")
            require(version in 35..41) { "Unsupported DEX version $version" }
            require(u4(bytes, base + 0x28) == ENDIAN_CONSTANT) { "Big-endian DEX is not supported" }
            val fileSize = u4(bytes, base + 0x20)
            // Version 041 (Android 15 containers) stores offsets relative to the container; earlier versions to the file.
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
                val classIdx = u4(bytes, classDefsOff + i * 32)
                require(classIdx < typeIdsSize) { "class_idx out of range" }
                val descriptorIdx = u4(bytes, typeIdsOff + classIdx * 4)
                require(descriptorIdx < stringIdsSize) { "descriptor_idx out of range" }
                val descriptor = string(bytes, origin + u4(bytes, stringIdsOff + descriptorIdx * 4))
                if (descriptor.length > 2 && descriptor[0] == 'L' && descriptor.last() == ';') {
                    names.add(descriptor.substring(1, descriptor.length - 1).replace('/', '.'))
                }
            }
            if (version < 41 || fileSize <= HEADER_SIZE) break
            require(fileSize <= bytes.size - base) { "DEX file_size exceeds container" }
            base += fileSize
        }
        return names
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
