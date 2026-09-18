/* SPDX-License-Identifier: Apache-2.0 */
package io.github.n_a_monterocarvajal.frankupdater.compatibility

/**
 * Chooses which installed-bundle config splits a device needs, using the naming convention
 * bundletool emits (`config.<abi>`, `config.<density>`, `<module>.config.<...>`).
 *
 * Per module, keeps the single best ABI split (device ABI order, as bundletool's AbiMatcher)
 * and the single best density split (ScreenDensitySelector). Every other split, including
 * language and feature splits, is kept.
 */
object ConfigSplitSelector {
    private val densities = mapOf(
        "ldpi" to 120, "mdpi" to 160, "tvdpi" to 213, "hdpi" to 240,
        "xhdpi" to 320, "xxhdpi" to 480, "xxxhdpi" to 640,
    )
    private val abis = listOf("armeabi", "armeabi-v7a", "arm64-v8a", "x86", "x86_64", "mips", "mips64", "riscv64")

    /** Returns the indexes of [splitNames] to install; `null` names are base APKs and always kept. */
    fun select(splitNames: List<String?>, supportedAbis: List<String>, densityDpi: Int): Set<Int> {
        val abiSplits = mutableMapOf<String, MutableMap<String, Int>>()
        val densitySplits = mutableMapOf<String, MutableMap<Int, Int>>()
        val keep = mutableSetOf<Int>()
        splitNames.forEachIndexed { index, name ->
            val config = if (name != null && "config." in name) name.substringAfter("config.") else null
            val module = name?.substringBefore("config.").orEmpty()
            val abi = abis.firstOrNull { it.replace('-', '_') == config }
            val dpi = config?.let(densities::get)
            when {
                abi != null -> abiSplits.getOrPut(module) { mutableMapOf() }[abi] = index
                dpi != null -> densitySplits.getOrPut(module) { mutableMapOf() }[dpi] = index
                else -> keep += index
            }
        }
        abiSplits.values.forEach { byAbi ->
            val abi = supportedAbis.firstOrNull { it in byAbi }
                ?: throw IllegalArgumentException("Ningún split de ABI es compatible con ${supportedAbis.joinToString()}.")
            keep += byAbi.getValue(abi)
        }
        densitySplits.values.forEach { byDpi ->
            keep += byDpi.getValue(ScreenDensitySelector.selectBestDensity(byDpi.keys, densityDpi))
        }
        return keep
    }
}
