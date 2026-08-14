/*
 * Copyright (C) 2018 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from bundletool's ScreenDensitySelector at
 * 586a43a450712a1067f3d92cf7574dee68226302.
 */
package io.github.n_a_monterocarvajal.frankupdater.compatibility

/** Selects densities using Android's asymmetric resource-scaling preference. */
object ScreenDensitySelector {
    const val DEFAULT_DENSITY = 0
    const val MDPI = 160
    const val ANY_DENSITY = 0xfffe
    const val NO_DENSITY = 0xffff

    fun selectBestDensity(
        densities: Iterable<Int>,
        desiredDpi: Int,
    ): Int {
        require(desiredDpi != NO_DENSITY) { "Device density cannot be NO_DENSITY" }
        val candidates = densities.distinct().sorted()
        require(candidates.isNotEmpty()) { "At least one density is required" }
        return candidates.maxWith(DensityComparator(desiredDpi))
    }

    private class DensityComparator(desiredDpi: Int) : Comparator<Int> {
        private val desiredDpi = when (desiredDpi) {
            DEFAULT_DENSITY, ANY_DENSITY -> MDPI
            else -> desiredDpi
        }

        override fun compare(left: Int, right: Int): Int {
            if (left == right) return 0
            if (left == ANY_DENSITY) return 1
            if (right == ANY_DENSITY) return -1
            return if (left > right) {
                -compareOrdered(lowerDpi = right, higherDpi = left)
            } else {
                compareOrdered(lowerDpi = left, higherDpi = right)
            }
        }

        private fun compareOrdered(lowerDpi: Int, higherDpi: Int): Int {
            if (desiredDpi >= higherDpi) return -1
            if (desiredDpi <= lowerDpi) return 1

            val scaledDownCost = ((2L * lowerDpi) - desiredDpi) * higherDpi
            val exactCost = desiredDpi.toLong() * desiredDpi
            return if (scaledDownCost > exactCost) 1 else -1
        }
    }
}
