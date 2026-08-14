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

    @Test
    fun `maximum sdk is respected`() {
        val result = checker.check(
            candidate = candidate(maxSdk = 25),
            device = device(sdk = 26),
        )

        assertEquals(
            CompatibilityResult.Incompatible(setOf(IncompatibilityReason.MaxSdk)),
            result,
        )
    }

    @Test
    fun `minimum installable target sdk is respected on recent Android`() {
        val result = checker.check(
            candidate = candidate(targetSdk = 23),
            device = device(sdk = 37),
        )

        assertEquals(
            CompatibilityResult.Incompatible(setOf(IncompatibilityReason.TargetSdk)),
            result,
        )
    }

    @Test
    fun `target sdk policy does not affect older Android`() {
        val result = checker.check(
            candidate = candidate(targetSdk = 1),
            device = device(sdk = 33),
        )

        assertEquals(CompatibilityResult.Compatible, result)
    }

    @Test
    fun `native code must support a device abi`() {
        val result = checker.check(
            candidate = candidate(abis = listOf("x86", "x86_64")),
            device = device(sdk = 28, supportedAbis = listOf("arm64-v8a", "armeabi-v7a")),
        )

        assertEquals(
            CompatibilityResult.Incompatible(setOf(IncompatibilityReason.Abi)),
            result,
        )
    }

    @Test
    fun `apk without native code is universal`() {
        val result = checker.check(
            candidate = candidate(abis = emptyList()),
            device = device(sdk = 28, supportedAbis = listOf("x86_64")),
        )

        assertEquals(CompatibilityResult.Compatible, result)
    }

    private fun candidate(
        minSdk: Int? = null,
        maxSdk: Int? = null,
        targetSdk: Int? = 28,
        abis: List<String> = emptyList(),
        requiredFeatures: Set<String> = emptySet(),
    ) = ArtifactCandidate(
        packageName = "org.example.app",
        versionName = "1.0",
        versionCode = 1,
        source = Source.GooglePlay,
        minSdk = minSdk,
        maxSdk = maxSdk,
        targetSdk = targetSdk,
        abis = abis,
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
        supportedAbis: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
    ) = GenericDeviceProfile(
        sdk = sdk,
        codename = null,
        supportedAbis = supportedAbis,
        densityDpi = 420,
        locales = listOf("es-CL"),
        systemFeatures = features,
    )
}
