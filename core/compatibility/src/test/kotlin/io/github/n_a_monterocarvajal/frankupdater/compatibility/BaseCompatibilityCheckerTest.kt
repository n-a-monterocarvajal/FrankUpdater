package io.github.n_a_monterocarvajal.frankupdater.compatibility

import io.github.n_a_monterocarvajal.frankupdater.model.ArtifactCandidate
import io.github.n_a_monterocarvajal.frankupdater.model.DownloadMode
import io.github.n_a_monterocarvajal.frankupdater.model.GenericDeviceProfile
import io.github.n_a_monterocarvajal.frankupdater.model.PackageType
import io.github.n_a_monterocarvajal.frankupdater.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class BaseCompatibilityCheckerTest {
    private val checker = BaseCompatibilityChecker()

    @Test
    fun `candidate above device sdk is rejected`() {
        val result = checker.check(
            candidate = candidate(minSdk = 27),
            device = device(sdk = 26),
        )

        assertEquals(
            CompatibilityResult.Incompatible(setOf(IncompatibilityReason.MinSdk)),
            result,
        )
    }

    @Test
    fun `matching sdk and required features are accepted`() {
        val result = checker.check(
            candidate = candidate(minSdk = 23, requiredFeatures = setOf("android.hardware.camera")),
            device = device(sdk = 26, features = setOf("android.hardware.camera")),
        )

        assertEquals(CompatibilityResult.Compatible, result)
    }

    private fun candidate(
        minSdk: Int?,
        requiredFeatures: Set<String> = emptySet(),
    ) = ArtifactCandidate(
        packageName = "org.example.app",
        versionName = "1.0",
        versionCode = 1,
        source = Source.GooglePlay,
        minSdk = minSdk,
        maxSdk = null,
        targetSdk = null,
        abis = emptyList(),
        densityDpi = null,
        locales = emptyList(),
        requiredFeatures = requiredFeatures,
        packageType = PackageType.MonolithicApk,
        signerDigests = emptySet(),
        artifacts = emptyList(),
        metadataUrl = null,
        downloadMode = DownloadMode.Unavailable,
    )

    private fun device(
        sdk: Int,
        features: Set<String> = emptySet(),
    ) = GenericDeviceProfile(
        sdk = sdk,
        codename = null,
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        densityDpi = 420,
        locales = listOf("es-CL"),
        systemFeatures = features,
    )
}
