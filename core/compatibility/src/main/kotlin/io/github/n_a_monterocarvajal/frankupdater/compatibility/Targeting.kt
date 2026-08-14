/*
 * Copyright (C) 2017-2018 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from bundletool targeting matchers at
 * 586a43a450712a1067f3d92cf7574dee68226302.
 */
package io.github.n_a_monterocarvajal.frankupdater.compatibility

data class AbiTargeting(
    val values: Set<String> = emptySet(),
    val alternatives: Set<String> = emptySet(),
)

data class MultiAbiTargeting(
    val values: Set<Set<String>> = emptySet(),
    val alternatives: Set<Set<String>> = emptySet(),
)

data class DensityTargeting(
    val values: Set<Int> = emptySet(),
    val alternatives: Set<Int> = emptySet(),
)

data class SdkTargeting(
    val minSdk: Int? = null,
    val alternatives: Set<Int> = emptySet(),
)

/** Minimal provider-neutral port of bundletool's ABI, density, and SDK targeting semantics. */
class SplitTargetingMatcher {
    fun matchesAbi(
        targeting: AbiTargeting,
        supportedAbis: List<String>,
    ): Boolean {
        require((targeting.values intersect targeting.alternatives).isEmpty()) {
            "ABI targeting values and alternatives must be mutually exclusive"
        }
        if (targeting.values.isEmpty() && targeting.alternatives.isEmpty()) return true

        supportedAbis.forEach { abi ->
            if (abi in targeting.values) return true
            if (abi in targeting.alternatives) return false
        }

        return targeting.values.isEmpty()
    }

    fun matchesMultiAbi(
        targeting: MultiAbiTargeting,
        supportedAbis: Set<String>,
    ): Boolean {
        if (targeting.values.isEmpty() && targeting.alternatives.isEmpty()) return true
        val matchingValues = targeting.values.filter(supportedAbis::containsAll)
        if (matchingValues.isEmpty()) return false

        return targeting.alternatives
            .filter(supportedAbis::containsAll)
            .none { alternative ->
                matchingValues.all { value -> compareAbiSets(alternative, value) > 0 }
            }
    }

    fun matchesDensity(
        targeting: DensityTargeting,
        deviceDensityDpi: Int,
    ): Boolean {
        val densities = targeting.values + targeting.alternatives
        if (densities.isEmpty()) return true
        return ScreenDensitySelector.selectBestDensity(densities, deviceDensityDpi) in
            targeting.values
    }

    fun matchesSdk(
        targeting: SdkTargeting,
        deviceSdk: Int,
        deviceCodename: String?,
    ): Boolean {
        val value = targeting.minSdk
        if (!matchesDeviceSdk(value, deviceSdk, deviceCodename)) return false

        return targeting.alternatives.none { alternative ->
            matchesDeviceSdk(alternative, deviceSdk, deviceCodename) &&
                alternative > (value ?: 0)
        }
    }

    private fun matchesDeviceSdk(
        minSdk: Int?,
        deviceSdk: Int,
        deviceCodename: String?,
    ): Boolean {
        val isPreRelease = !deviceCodename.isNullOrEmpty() && deviceCodename != RELEASE_CODENAME
        if (isPreRelease && minSdk == PRE_RELEASE_SDK) return true
        return minSdk == null || minSdk <= deviceSdk
    }

    private fun compareAbiSets(left: Set<String>, right: Set<String>): Int {
        val leftRanks = left.map(::abiRank).sortedDescending()
        val rightRanks = right.map(::abiRank).sortedDescending()
        val sharedSize = minOf(leftRanks.size, rightRanks.size)
        repeat(sharedSize) { index ->
            leftRanks[index].compareTo(rightRanks[index])
                .takeIf { it != 0 }
                ?.let { return it }
        }
        return leftRanks.size.compareTo(rightRanks.size)
    }

    private fun abiRank(abi: String): Int = ABI_ORDER.indexOf(abi).also { rank ->
        require(rank >= 0) { "Unrecognized ABI '$abi'" }
    }

    private companion object {
        const val RELEASE_CODENAME = "REL"
        const val PRE_RELEASE_SDK = 10_000
        val ABI_ORDER = listOf(
            "armeabi",
            "armeabi-v7a",
            "arm64-v8a",
            "x86",
            "x86_64",
            "mips",
            "mips64",
            "riscv64",
        )
    }
}
