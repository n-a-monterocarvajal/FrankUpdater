package io.github.n_a_monterocarvajal.frankupdater.compatibility

import io.github.n_a_monterocarvajal.frankupdater.model.ArtifactCandidate
import io.github.n_a_monterocarvajal.frankupdater.model.GenericDeviceProfile

enum class IncompatibilityReason {
    MinSdk,
    MaxSdk,
    TargetSdk,
    Abi,
    RequiredFeature,
}

sealed interface CompatibilityResult {
    data object Compatible : CompatibilityResult

    data class Incompatible(
        val reasons: Set<IncompatibilityReason>,
    ) : CompatibilityResult
}

fun interface CompatibilityChecker {
    fun check(
        candidate: ArtifactCandidate,
        device: GenericDeviceProfile,
    ): CompatibilityResult
}

/**
 * Provider-independent APK compatibility gates.
 *
 * Adapted from F-Droid client's `CompatibilityCheckerImpl` at
 * 707b8ece6e5eece0a27b80855172e12e624f9c71 (GPL-3.0-only). Targeting among split
 * alternatives remains in [SplitTargetingMatcher].
 */
class BaseCompatibilityChecker : CompatibilityChecker {
    override fun check(
        candidate: ArtifactCandidate,
        device: GenericDeviceProfile,
    ): CompatibilityResult {
        val minSdk = candidate.minSdk
        val maxSdk = candidate.maxSdk
        val targetSdk = candidate.targetSdk ?: 1
        val reasons = buildSet {
            if (minSdk != null && device.sdk < minSdk) {
                add(IncompatibilityReason.MinSdk)
            }
            if (maxSdk != null && device.sdk > maxSdk) {
                add(IncompatibilityReason.MaxSdk)
            }
            if (targetSdk < CompatibilityCheckerUtils.minInstallableTargetSdk(device.sdk)) {
                add(IncompatibilityReason.TargetSdk)
            }
            if (
                candidate.abis.isNotEmpty() &&
                candidate.abis.none(device.supportedAbis::contains)
            ) {
                add(IncompatibilityReason.Abi)
            }
            if (!device.systemFeatures.containsAll(candidate.requiredFeatures)) {
                add(IncompatibilityReason.RequiredFeature)
            }
        }

        return if (reasons.isEmpty()) {
            CompatibilityResult.Compatible
        } else {
            CompatibilityResult.Incompatible(reasons)
        }
    }
}

/** Helpers mirrored from platform installation policy through F-Droid's checker. */
object CompatibilityCheckerUtils {
    /**
     * Minimum target SDK accepted by the Android package installer for a new installation.
     * Keep synchronized with AOSP's `MIN_INSTALLABLE_TARGET_SDK` policy.
     */
    fun minInstallableTargetSdk(sdk: Int): Int = when (sdk) {
        34 -> 23
        35, 36, 37 -> 24
        else -> 1
    }
}
