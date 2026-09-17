/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.play

import io.github.n_a_monterocarvajal.frankupdater.compatibility.CatalogEntry
import io.github.n_a_monterocarvajal.frankupdater.model.*

/** Play's offered version is not a complete manifest or a complete release history. */
internal fun playCatalogEntry(packageName: String, versionCode: Long, versionName: String?) = CatalogEntry(
    ArtifactCandidate(packageName, versionName, versionCode, Source.GooglePlay,
        minSdk = null, maxSdk = null, targetSdk = null, abis = emptyList(), densityDpi = null,
        locales = emptyList(), requiredFeatures = emptySet(), packageType = PackageType.SplitApkSet,
        signerDigests = emptySet(), artifacts = emptyList(),
        metadataUrl = "https://play.google.com/store/apps/details?id=$packageName", downloadMode = DownloadMode.ResolvableDirect),
)
