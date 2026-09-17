package io.github.n_a_monterocarvajal.frankupdater.sources

import io.github.n_a_monterocarvajal.frankupdater.archive.PackageArchiveExtractor
import io.github.n_a_monterocarvajal.frankupdater.model.PackageType
import io.github.n_a_monterocarvajal.frankupdater.model.Source
import io.github.n_a_monterocarvajal.frankupdater.verification.*
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in only; ordinary builds never contact providers or download applications. */
class WebSourcesLiveTest {
    @Test fun pureDownloadPassesHashManifestAndSignatureChecksWithoutAndroid() {
        assumeTrue(System.getenv("FRANK_LIVE_WEB") == "1")
        val directory = Files.createTempDirectory("frank-pure-live").toFile()
        var phase = "history"
        try {
            val client = WebSourceClient()
            val packageName = "org.fossify.math"
            val entries = PureParser.history(client.text(
                "https://tapi.pureapk.com/v3/get_app_his_version?package_name=$packageName&hl=en", Source.ApkPure,
                mapOf("Ual-Access-Businessid" to "projecta", "Ual-Access-ProjectA" to "{\"device_info\":{\"os_ver\":\"26\"}}")), packageName)
            val candidate = entries.first { entry ->
                val artifact = entry.artifact
                artifact.minSdk?.let { it <= 26 } == true &&
                    (artifact.abis.isEmpty() || "arm64-v8a" in artifact.abis) &&
                    artifact.artifacts.single().sizeBytes?.let { it in 1..25_000_000 } == true &&
                    artifact.artifacts.single().sha256 != null
            }.artifact
            val asset = candidate.artifacts.single()
            val extension = if (candidate.packageType == PackageType.Xapk) "xapk" else "apk"
            phase = "download and SHA-256"
            val file = client.download(asset.uri, Source.ApkPure, File(directory, "download.$extension"),
                expectedSha256 = asset.sha256, expectedSize = asset.sizeBytes)
            phase = "archive and signatures"
            val archive = PackageArchiveExtractor().extract(file, file.name, File(directory, "extracted"), asset.sha256)
            val parsed = archive.apks.map { apk ->
                val manifest = BinaryManifestMetadataReader().read(apk.file)
                val signature = SignatureVerifier().inspect(apk.file, 26)
                ParsedApk(apk.entryName, apk.file, apk.sha256, manifest.packageName, candidate.versionName,
                    manifest.versionCode, manifest.splitName, manifest.minSdk, manifest.targetSdk,
                    signature.signers, signature.lineage, true, emptyList(), signature.authorizedAncestors)
            }
            assertEquals(InstallAction.Install, PackageSetValidator().validate(parsed,
                VerificationExpectations(packageName, candidate.versionCode), null))
            assertTrue(parsed.single { it.isBase }.minSdk!! <= 26)
            println("APKPure: version=${candidate.versionCode}, format=$extension, bytes=${file.length()}, verifiedApks=${parsed.size}")
        } catch (error: Exception) {
            // Do not put temporary signed URLs in reports or CI logs.
            throw AssertionError("$phase: ${error.javaClass.simpleName} " + Regex("HTTP \\d{3}").find(error.message.orEmpty())?.value.orEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun mirrorHistoryAndVariantsMatchTheLiveContract() {
        assumeTrue(System.getenv("FRANK_LIVE_WEB") == "1")
        var phase = "history"
        try {
            val client = WebSourceClient()
            val url = "https://www.apkmirror.com/apk/fossify/fossify-calculator/"
            val releases = MirrorParser.releases(client.text(url, Source.ApkMirror), url)
            phase = "variants"
            val release = releases.first()
            val variants = MirrorParser.variants(client.text(release.url, Source.ApkMirror), release.url)
            assertTrue(variants.isNotEmpty())
            assertTrue(variants.any { it.versionCode != null })
            println("APKMirror: releases=${releases.size}, variants=${variants.size}, numericCodes=${variants.count { it.versionCode != null }}")
        } catch (error: Exception) {
            throw AssertionError("$phase: ${error.javaClass.simpleName} " + Regex("HTTP \\d{3}").find(error.message.orEmpty())?.value.orEmpty())
        }
    }
}
