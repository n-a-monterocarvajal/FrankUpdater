/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Binary manifest classification follows App Manager's ApkFile approach at
 * fc1e70074e8cf75c0619e526e688c16ad2e1e862. Identity fields use apksig's
 * public ApkUtils API; only the root AXML `split` attribute is decoded here.
 */
package io.github.n_a_monterocarvajal.frankupdater.verification

import com.android.apksig.apk.ApkUtils
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.zip.ZipFile

internal data class BinaryManifestMetadata(
    val packageName: String,
    val versionCode: Long,
    val splitName: String?,
    val minSdk: Int?,
    val targetSdk: Int?,
)

internal class BinaryManifestMetadataReader {
    private val rootAttributeReader = BinaryXmlRootAttributeReader()

    fun read(apk: File): BinaryManifestMetadata {
        val bytes = ZipFile(apk).use { zip ->
            val entry = zip.getEntry(ANDROID_MANIFEST)
                ?: error("El APK no contiene $ANDROID_MANIFEST")
            require(entry.size in 1..MAX_MANIFEST_BYTES.toLong()) { "Manifiesto demasiado grande." }
            zip.getInputStream(entry).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() <= MAX_MANIFEST_BYTES - count) { "Manifiesto demasiado grande." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
        fun manifestBuffer(): ByteBuffer = ByteBuffer.wrap(bytes)
        return BinaryManifestMetadata(
            packageName = ApkUtils.getPackageNameFromBinaryAndroidManifest(manifestBuffer()),
            versionCode = ApkUtils.getLongVersionCodeFromBinaryAndroidManifest(manifestBuffer()),
            splitName = rootAttributeReader.read(bytes, SPLIT_ATTRIBUTE),
            minSdk = runCatching {
                ApkUtils.getMinSdkVersionFromBinaryAndroidManifest(manifestBuffer())
            }.getOrNull(),
            targetSdk = runCatching {
                ApkUtils.getTargetSdkVersionFromBinaryAndroidManifest(manifestBuffer())
            }.getOrNull(),
        )
    }

    private companion object {
        const val ANDROID_MANIFEST = "AndroidManifest.xml"
        const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
        const val SPLIT_ATTRIBUTE = "split"
    }
}

/** Reads one un-namespaced attribute from the root element of Android binary XML. */
internal class BinaryXmlRootAttributeReader {
    fun read(bytes: ByteArray, attributeName: String): String? {
        val input = LittleEndianBytes(bytes)
        require(input.u16(0) == RES_XML_TYPE) { "El manifiesto no es XML binario de Android." }
        val documentHeaderSize = input.u16(2)
        val documentSize = input.chunkSize(0, documentHeaderSize)
        var stringPool: BinaryStringPool? = null
        var offset = documentHeaderSize
        while (offset < documentSize) {
            val type = input.u16(offset)
            val headerSize = input.u16(offset + 2)
            val chunkSize = input.chunkSize(offset, headerSize, documentSize)
            when (type) {
                RES_STRING_POOL_TYPE -> stringPool = BinaryStringPool(input, offset, headerSize, chunkSize)
                RES_XML_START_ELEMENT_TYPE -> {
                    val pool = requireNotNull(stringPool) { "El XML binario no contiene string pool." }
                    return readRootAttribute(input, pool, offset, headerSize, chunkSize, attributeName)
                }
            }
            offset += chunkSize
        }
        error("El XML binario no contiene elemento raíz.")
    }

    private fun readRootAttribute(
        input: LittleEndianBytes,
        pool: BinaryStringPool,
        chunkOffset: Int,
        headerSize: Int,
        chunkSize: Int,
        requestedName: String,
    ): String? {
        val extension = chunkOffset + headerSize
        require(pool.string(input.u32Index(extension + 4)) == MANIFEST_ELEMENT) {
            "El XML binario no tiene una raíz manifest."
        }
        val attributeStart = input.u16(extension + 8)
        val attributeSize = input.u16(extension + 10)
        val attributeCount = input.u16(extension + 12)
        require(attributeSize >= ATTRIBUTE_MIN_SIZE) { "Tamaño de atributo AXML inválido." }
        val attributesOffset = extension + attributeStart
        val chunkEnd = chunkOffset + chunkSize
        repeat(attributeCount) { index ->
            val attributeOffset = attributesOffset + index * attributeSize
            input.requireRange(attributeOffset, attributeSize, chunkEnd)
            val name = pool.string(input.u32Index(attributeOffset + 4))
            if (name == requestedName && input.u32(attributeOffset) == NO_INDEX) {
                val rawValueIndex = input.u32(attributeOffset + 8)
                val dataType = input.u8(attributeOffset + 15)
                val data = input.u32(attributeOffset + 16)
                val valueIndex = when {
                    rawValueIndex != NO_INDEX -> input.index(rawValueIndex)
                    dataType == TYPE_STRING -> input.index(data)
                    else -> error("El atributo $requestedName no contiene una cadena.")
                }
                return pool.string(valueIndex).takeIf(String::isNotBlank)
            }
        }
        return null
    }

    private companion object {
        const val RES_XML_TYPE = 0x0003
        const val RES_STRING_POOL_TYPE = 0x0001
        const val RES_XML_START_ELEMENT_TYPE = 0x0102
        const val TYPE_STRING = 0x03
        const val ATTRIBUTE_MIN_SIZE = 20
        const val NO_INDEX = 0xffffffffL
        const val MANIFEST_ELEMENT = "manifest"
    }
}

private class BinaryStringPool(
    private val input: LittleEndianBytes,
    private val chunkOffset: Int,
    headerSize: Int,
    private val chunkSize: Int,
) {
    private val stringCount = input.u32Index(chunkOffset + 8)
    private val utf8 = input.u32(chunkOffset + 16) and UTF8_FLAG != 0L
    private val stringsStart = input.u32Index(chunkOffset + 20)
    private val offsetsStart = chunkOffset + headerSize

    fun string(index: Int): String {
        require(index in 0 until stringCount) { "Índice de cadena AXML fuera de rango." }
        val relativeOffset = input.u32Index(offsetsStart + index * Int.SIZE_BYTES)
        val start = chunkOffset + stringsStart + relativeOffset
        return if (utf8) readUtf8(start) else readUtf16(start)
    }

    private fun readUtf8(start: Int): String {
        val (_, afterUtf16Length) = input.utf8Length(start, chunkEnd())
        val (byteLength, dataStart) = input.utf8Length(afterUtf16Length, chunkEnd())
        input.requireRange(dataStart, byteLength, chunkEnd())
        return input.string(dataStart, byteLength, Charsets.UTF_8)
    }

    private fun readUtf16(start: Int): String {
        val (characterCount, dataStart) = input.utf16Length(start, chunkEnd())
        val byteLength = Math.multiplyExact(characterCount, 2)
        input.requireRange(dataStart, byteLength, chunkEnd())
        return input.string(dataStart, byteLength, Charsets.UTF_16LE)
    }

    private fun chunkEnd(): Int = chunkOffset + chunkSize

    private companion object {
        const val UTF8_FLAG = 0x00000100L
    }
}

private class LittleEndianBytes(private val bytes: ByteArray) {
    fun u8(offset: Int): Int {
        requireRange(offset, 1)
        return bytes[offset].toInt() and 0xff
    }

    fun u16(offset: Int): Int {
        requireRange(offset, 2)
        return u8(offset) or (u8(offset + 1) shl 8)
    }

    fun u32(offset: Int): Long {
        requireRange(offset, 4)
        return u8(offset).toLong() or
            (u8(offset + 1).toLong() shl 8) or
            (u8(offset + 2).toLong() shl 16) or
            (u8(offset + 3).toLong() shl 24)
    }

    fun u32Index(offset: Int): Int = index(u32(offset))

    fun index(value: Long): Int {
        require(value <= Int.MAX_VALUE) { "Valor AXML demasiado grande." }
        return value.toInt()
    }

    fun chunkSize(offset: Int, headerSize: Int, parentEnd: Int = bytes.size): Int {
        require(headerSize >= CHUNK_HEADER_SIZE) { "Cabecera AXML inválida." }
        val size = u32Index(offset + 4)
        require(size >= headerSize) { "Tamaño de chunk AXML inválido." }
        requireRange(offset, size, parentEnd)
        return size
    }

    fun utf8Length(offset: Int, end: Int): Pair<Int, Int> {
        requireRange(offset, 1, end)
        val first = u8(offset)
        return if (first and 0x80 == 0) {
            first to offset + 1
        } else {
            requireRange(offset, 2, end)
            (((first and 0x7f) shl 8) or u8(offset + 1)) to offset + 2
        }
    }

    fun utf16Length(offset: Int, end: Int): Pair<Int, Int> {
        requireRange(offset, 2, end)
        val first = u16(offset)
        return if (first and 0x8000 == 0) {
            first to offset + 2
        } else {
            requireRange(offset, 4, end)
            (((first and 0x7fff) shl 16) or u16(offset + 2)) to offset + 4
        }
    }

    fun string(offset: Int, length: Int, charset: Charset): String {
        requireRange(offset, length)
        return String(bytes, offset, length, charset)
    }

    fun requireRange(offset: Int, length: Int, end: Int = bytes.size) {
        require(offset >= 0 && length >= 0 && end in 0..bytes.size && offset <= end - length) {
            "Estructura AXML truncada."
        }
    }

    private companion object {
        const val CHUNK_HEADER_SIZE = 8
    }
}
