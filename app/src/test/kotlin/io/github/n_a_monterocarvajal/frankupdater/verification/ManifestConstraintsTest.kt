package io.github.n_a_monterocarvajal.frankupdater.verification

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class ManifestConstraintsTest {
    @Test fun `required features and max SDK are read while optional features are ignored`() {
        val result = readManifestConstraints(xml(listOf(
            "uses-sdk" to mapOf("maxSdkVersion" to 28),
            "uses-feature" to mapOf("name" to "android.hardware.camera"),
            "uses-feature" to mapOf("name" to "android.hardware.nfc", "required" to 0),
        )))
        assertEquals(28, result.maxSdk)
        assertEquals(setOf("android.hardware.camera"), result.requiredFeatures)
        assertTrue(result.complete)
    }

    @Test fun `unsupported requirements and unresolved values prevent confirmation`() {
        for (element in listOf("uses-library" to mapOf("name" to "example.library"),
            "uses-feature" to mapOf("glEsVersion" to 196608),
            "uses-sdk" to mapOf("maxSdkVersion" to null),
            "application" to mapOf("isSplitRequired" to 1))) {
            assertFalse(readManifestConstraints(xml(listOf(element))).complete)
        }
        assertThrows(IllegalArgumentException::class.java) { readManifestConstraints(xml(emptyList()).copyOf(12)) }
    }

    private fun xml(elements: List<Pair<String, Map<String, Any?>>>): ByteArray {
        val strings = (listOf("manifest") + elements.flatMap { (name, attributes) ->
            listOf(name) + attributes.keys + attributes.values.filterIsInstance<String>()
        }).distinct()
        val encoded = strings.map { (it + '\u0000').toByteArray(Charsets.UTF_16LE) }
        val poolSize = 28 + 4 * strings.size + encoded.sumOf { it.size + 2 }
        val nodes = listOf("manifest" to emptyMap<String, Any?>()) + elements
        val total = 8 + poolSize + nodes.sumOf { 36 + 20 * it.second.size }
        val buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(3).putShort(8).putInt(total)
        buffer.putShort(1).putShort(28).putInt(poolSize).putInt(strings.size).putInt(0).putInt(0)
            .putInt(28 + 4 * strings.size).putInt(0)
        var position = 0
        encoded.forEach { buffer.putInt(position); position += it.size + 2 }
        encoded.forEachIndexed { index, bytes -> buffer.putShort(strings[index].length.toShort()).put(bytes) }
        nodes.forEach { (name, attributes) ->
            buffer.putShort(0x0102).putShort(16).putInt(36 + 20 * attributes.size).putInt(1).putInt(-1)
                .putInt(-1).putInt(strings.indexOf(name)).putShort(20).putShort(20)
                .putShort(attributes.size.toShort()).putShort(0).putShort(0).putShort(0)
            attributes.forEach { (key, value) ->
                buffer.putInt(-1).putInt(strings.indexOf(key)).putInt(-1).putShort(8).put(0)
                when (value) {
                    is String -> buffer.put(3).putInt(strings.indexOf(value))
                    is Int -> buffer.put(16).putInt(value)
                    else -> buffer.put(1).putInt(0x7f000001)
                }
            }
        }
        return buffer.array()
    }
}
