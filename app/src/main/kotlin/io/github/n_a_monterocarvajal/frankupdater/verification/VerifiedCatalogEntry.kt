/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.verification

import io.github.n_a_monterocarvajal.frankupdater.compatibility.CatalogEntry
import io.github.n_a_monterocarvajal.frankupdater.compatibility.ReleaseChannel
import io.github.n_a_monterocarvajal.frankupdater.model.*
import java.util.zip.ZipFile

/** Only verified bytes provide absence-of-constraint evidence; a provider's missing field never does. */
internal fun VerifiedPackageArchive.catalogEntry(source: Source, url: String, channel: ReleaseChannel): CatalogEntry {
    val base = apks.single { it.isBase }
    val manifest = BinaryManifestMetadataReader().read(base.file)
    val abis = apks.flatMap { apk ->
        ZipFile(apk.file).use { zip ->
            zip.entries().asSequence().map { it.name }
                .filter { it.startsWith("lib/") && it.endsWith(".so") && it.count { char -> char == '/' } == 2 }
                .map { it.split('/')[1] }.toList()
        }
    }.distinct()
    return CatalogEntry(ArtifactCandidate(packageName, versionName, versionCode, source,
        minSdk, manifest.constraints.maxSdk, targetSdk, abis, null, emptyList(), manifest.constraints.requiredFeatures,
        if (apks.size == 1) PackageType.MonolithicApk else PackageType.SplitApkSet,
        signerDigests, emptyList(), url, DownloadMode.Unavailable),
        // Split dependency/targeting evidence is not represented by a flat union of native libraries.
        constraintsKnown = apks.size == 1 && manifest.constraints.complete && minSdk != null && targetSdk != null,
        channel = channel)
}
