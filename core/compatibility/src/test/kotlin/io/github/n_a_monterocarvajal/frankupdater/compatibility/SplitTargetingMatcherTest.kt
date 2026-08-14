/*
 * Copyright (C) 2018 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 *
 * Parity cases ported from bundletool at 586a43a450712a1067f3d92cf7574dee68226302.
 */
package io.github.n_a_monterocarvajal.frankupdater.compatibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitTargetingMatcherTest {
    private val matcher = SplitTargetingMatcher()

    @Test
    fun `abi alternatives preserve device preference order`() {
        val deviceAbis = listOf("x86_64", "x86", "arm64-v8a")

        assertFalse(
            matcher.matchesAbi(
                targeting = AbiTargeting(
                    values = setOf("arm64-v8a"),
                    alternatives = setOf("x86_64"),
                ),
                supportedAbis = deviceAbis,
            ),
        )
        assertTrue(
            matcher.matchesAbi(
                targeting = AbiTargeting(
                    values = setOf("x86_64"),
                    alternatives = setOf("arm64-v8a", "x86"),
                ),
                supportedAbis = deviceAbis,
            ),
        )
    }

    @Test
    fun `abi fallback targeting matches when no alternative is supported`() {
        assertTrue(
            matcher.matchesAbi(
                targeting = AbiTargeting(alternatives = setOf("x86_64")),
                supportedAbis = listOf("x86", "armeabi-v7a"),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `abi values and alternatives must be disjoint`() {
        matcher.matchesAbi(
            targeting = AbiTargeting(
                values = setOf("x86"),
                alternatives = setOf("x86"),
            ),
            supportedAbis = listOf("x86"),
        )
    }

    @Test
    fun `multi abi targeting requires the entire set`() {
        val targeting = MultiAbiTargeting(values = setOf(setOf("arm64-v8a", "armeabi-v7a")))

        assertTrue(
            matcher.matchesMultiAbi(
                targeting = targeting,
                supportedAbis = setOf("arm64-v8a", "armeabi-v7a"),
            ),
        )
        assertFalse(
            matcher.matchesMultiAbi(
                targeting = targeting,
                supportedAbis = setOf("arm64-v8a"),
            ),
        )
    }

    @Test
    fun `multi abi targeting rejects a value when a better architecture set matches`() {
        assertFalse(
            matcher.matchesMultiAbi(
                targeting = MultiAbiTargeting(
                    values = setOf(setOf("x86")),
                    alternatives = setOf(setOf("x86_64")),
                ),
                supportedAbis = setOf("x86", "x86_64"),
            ),
        )
    }

    @Test
    fun `density selector prefers lower dpi when Android scaling formula does`() {
        assertEquals(
            213,
            ScreenDensitySelector.selectBestDensity(
                densities = listOf(213, 320),
                desiredDpi = 240,
            ),
        )
    }

    @Test
    fun `density selector prefers higher dpi when downscaling is cheaper`() {
        assertEquals(
            240,
            ScreenDensitySelector.selectBestDensity(
                densities = listOf(120, 240),
                desiredDpi = 160,
            ),
        )
    }

    @Test
    fun `any density always wins`() {
        assertEquals(
            ScreenDensitySelector.ANY_DENSITY,
            ScreenDensitySelector.selectBestDensity(
                densities = listOf(160, 240, ScreenDensitySelector.ANY_DENSITY, 480),
                desiredDpi = 420,
            ),
        )
    }

    @Test
    fun `density targeting only matches the best split`() {
        val values = setOf(320)
        val alternatives = setOf(480)

        assertTrue(
            matcher.matchesDensity(
                targeting = DensityTargeting(values, alternatives),
                deviceDensityDpi = 360,
            ),
        )
        assertFalse(
            matcher.matchesDensity(
                targeting = DensityTargeting(values, alternatives),
                deviceDensityDpi = 480,
            ),
        )
    }

    @Test
    fun `sdk targeting rejects a value when a better alternative matches`() {
        assertFalse(
            matcher.matchesSdk(
                targeting = SdkTargeting(minSdk = 21, alternatives = setOf(23)),
                deviceSdk = 25,
                deviceCodename = "REL",
            ),
        )
        assertTrue(
            matcher.matchesSdk(
                targeting = SdkTargeting(minSdk = 23, alternatives = setOf(21)),
                deviceSdk = 25,
                deviceCodename = "REL",
            ),
        )
    }

    @Test
    fun `pre release sdk targeting only matches pre release devices`() {
        val targeting = SdkTargeting(minSdk = 10_000)

        assertFalse(matcher.matchesSdk(targeting, deviceSdk = 29, deviceCodename = "REL"))
        assertTrue(matcher.matchesSdk(targeting, deviceSdk = 29, deviceCodename = "R"))
    }
}
