package io.github.n_a_monterocarvajal.frankupdater.compatibility

import io.github.n_a_monterocarvajal.frankupdater.model.*
import org.junit.Assert.*
import org.junit.Test

class VersionCatalogTest {
    private val device = GenericDeviceProfile(26, "REL", listOf("arm64-v8a", "armeabi-v7a"), 420, listOf("es-CL"), emptySet())
    private val catalog = VersionCatalog()

    @Test fun `preview releases require opt in without hiding the latest publication`() {
        val entries = listOf(entry(100, Source.ApkMirror).copy(channel = ReleaseChannel.Preview),
            entry(99, Source.ApkPure))
        val stable = catalog.select("org.example.app", entries, device)
        assertEquals(100L, stable.latestKnownVersionCode)
        assertEquals(99L, stable.latestCompatibleVersionCode)
        assertTrue(stable.sourcesFor(100).isEmpty())
        assertEquals(100L, catalog.select("org.example.app", entries, device, includePreviews = true).latestCompatibleVersionCode)
    }

    @Test fun `numeric version order beats labels and provider priority`() {
        val result = catalog.select("org.example.app", listOf(
            entry(10, Source.GooglePlay), entry(100, Source.ApkPure, minSdk = 28), entry(99, Source.ApkMirror)), device)
        assertEquals(100L, result.latestKnownVersionCode)
        assertEquals(99L, result.latestCompatibleVersionCode)
        assertEquals(listOf(99L, 10L), result.compatibleVersions)
        assertEquals(setOf(IncompatibilityReason.MinSdk), result.assessments.first().incompatibilities)
    }

    @Test fun `unknown newer metadata does not become confirmed compatibility`() {
        val unknown = entry(100, Source.GooglePlay).copy(constraintsKnown = false)
        val result = catalog.select("org.example.app", listOf(unknown, entry(99, Source.ApkMirror)), device)
        assertEquals(99L, result.latestCompatibleVersionCode)
        assertTrue(result.hasUnresolvedNewerVersion)
        assertTrue(catalog.select("org.example.app", listOf(unknown), device).compatibleVersions.isEmpty())
    }

    @Test fun `source priority applies only to eligible equivalents and retains variants`() {
        val play = entry(99, Source.GooglePlay)
        val mirror = entry(99, Source.ApkMirror)
        val result = catalog.select("org.example.app", listOf(entry(99, Source.ApkPure), mirror, play,
            play.copy(artifact = play.artifact.copy(abis = listOf("x86")))), device)
        assertEquals(listOf(Source.GooglePlay, Source.ApkMirror, Source.ApkPure), result.sourcesFor(99).map { it.artifact.source })
        assertEquals(4, result.assessments.size)
    }

    @Test fun `installed version blocks reinstall and downgrade suggestions`() {
        for (installed in listOf(99L, 100L)) {
            val result = catalog.select("org.example.app", listOf(entry(99, Source.ApkMirror)), device, installed)
            assertNull(result.suggestedUpdateVersionCode)
            assertEquals(99L, result.latestCompatibleVersionCode)
        }
    }

    @Test fun `missing source never erases good data and unavailable downloads do not hide metadata`() {
        val item = entry(99, Source.ApkMirror).let { it.copy(artifact = it.artifact.copy(downloadMode = DownloadMode.Unavailable)) }
        val result = catalog.select("org.example.app", listOf(item, item), device, unavailableSources = setOf(Source.GooglePlay))
        assertEquals(1, result.assessments.size)
        assertEquals(setOf(Source.GooglePlay), result.unavailableSources)
        assertEquals(99L, result.latestCompatibleVersionCode)
        assertTrue(result.sourcesFor(99).isEmpty())
    }

    @Test fun `wrong package invalid version and empty catalogue are handled explicitly`() {
        assertNull(catalog.select("org.example.app", emptyList(), device).latestKnownVersionCode)
        assertThrows(IllegalArgumentException::class.java) { catalog.select("other.app", listOf(entry(99, Source.GooglePlay)), device) }
        assertThrows(IllegalArgumentException::class.java) { catalog.select("org.example.app", listOf(entry(0, Source.GooglePlay)), device) }
    }

    private fun entry(version: Long, source: Source, minSdk: Int = 23) = CatalogEntry(ArtifactCandidate(
        packageName = "org.example.app", versionName = "label-$version", versionCode = version, source = source,
        minSdk = minSdk, maxSdk = null, targetSdk = 35, abis = emptyList(), densityDpi = null,
        locales = emptyList(), requiredFeatures = emptySet(), packageType = PackageType.MonolithicApk,
        signerDigests = emptySet(), artifacts = emptyList(), metadataUrl = null, downloadMode = DownloadMode.Direct,
    ), constraintsKnown = true)
}
