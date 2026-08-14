package io.github.n_a_monterocarvajal.frankupdater.compatibility

import io.github.n_a_monterocarvajal.frankupdater.model.ArtifactCandidate
import io.github.n_a_monterocarvajal.frankupdater.model.GenericDeviceProfile

enum class IncompatibilityReason {
    MinSdk,
    MaxSdk,
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
 * Implements only provider-independent SDK and feature gates.
 *
 * ABI, density, locale and split targeting deliberately remain outside this class until the
 * corresponding bundletool semantics and parity tests are ported and registered in UPSTREAMS.yml.
 */
class BaseCompatibilityChecker : CompatibilityChecker {
    override fun check(
        candidate: ArtifactCandidate,
        device: GenericDeviceProfile,
    ): CompatibilityResult {
        val minSdk = candidate.minSdk
        val maxSdk = candidate.maxSdk
        val reasons = buildSet {
            if (minSdk != null && device.sdk < minSdk) {
                add(IncompatibilityReason.MinSdk)
            }
            if (maxSdk != null && device.sdk > maxSdk) {
                add(IncompatibilityReason.MaxSdk)
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
