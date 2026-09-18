/* SPDX-License-Identifier: Apache-2.0 */
package io.github.n_a_monterocarvajal.frankupdater.compatibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigSplitSelectorTest {
    // Split set observed in the Fossify Calculator 1.4.0 APKM from APKMirror.
    private val fossify = listOf(null, "config.arm64_v8a", "config.armeabi_v7a", "config.hdpi", "config.ldpi",
        "config.mdpi", "config.tvdpi", "config.x86", "config.x86_64", "config.xhdpi", "config.xxhdpi", "config.xxxhdpi")

    @Test fun `keeps base, preferred ABI and nearest density only`() {
        val kept = ConfigSplitSelector.select(fossify, listOf("x86_64", "arm64-v8a"), 420).map { fossify[it] }.toSet()
        assertEquals(setOf(null, "config.x86_64", "config.xxhdpi"), kept)
    }

    @Test fun `keeps language and feature splits, selects per module`() {
        val names = listOf(null, "config.en", "config.es", "camera", "camera.config.arm64_v8a", "camera.config.x86_64",
            "config.arm64_v8a")
        val kept = ConfigSplitSelector.select(names, listOf("arm64-v8a"), 160).map { names[it] }.toSet()
        assertEquals(setOf(null, "config.en", "config.es", "camera", "camera.config.arm64_v8a", "config.arm64_v8a"), kept)
    }

    @Test fun `rejects bundles without a compatible ABI split`() {
        assertThrows(IllegalArgumentException::class.java) {
            ConfigSplitSelector.select(listOf(null, "config.armeabi_v7a"), listOf("x86_64"), 420)
        }
    }
}
