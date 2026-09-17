/*
 * SPDX-License-Identifier: Apache-2.0
 * Descending selection and installed-version cutoff adapted from F-Droid
 * UpdateChecker at f71539794471a123441de16c775f7ff5ecaef460 (libs/LICENSE).
 */
package io.github.n_a_monterocarvajal.frankupdater.compatibility

import io.github.n_a_monterocarvajal.frankupdater.model.ArtifactCandidate
import io.github.n_a_monterocarvajal.frankupdater.model.DownloadMode
import io.github.n_a_monterocarvajal.frankupdater.model.GenericDeviceProfile
import io.github.n_a_monterocarvajal.frankupdater.model.Source

/** True only when the source supplied all compatibility fields represented by ArtifactCandidate. */
enum class ReleaseChannel { Unknown, Stable, Preview }

data class CatalogEntry(
    val artifact: ArtifactCandidate,
    val constraintsKnown: Boolean = false,
    val channel: ReleaseChannel = ReleaseChannel.Unknown,
)

data class CatalogAssessment(
    val entry: CatalogEntry,
    val incompatibilities: Set<IncompatibilityReason>,
    val needsMetadata: Boolean,
    val channelAllowed: Boolean = true,
) {
    val compatibleByMetadata: Boolean get() = incompatibilities.isEmpty() && !needsMetadata && channelAllowed
}

data class VersionSelection(
    val assessments: List<CatalogAssessment>,
    val unavailableSources: Set<Source>,
    val installedVersionCode: Long?,
) {
    val latestKnownVersionCode: Long? get() = assessments.firstOrNull()?.entry?.artifact?.versionCode
    val compatibleVersions: List<Long> get() = assessments.filter { it.compatibleByMetadata }
        .map { it.entry.artifact.versionCode }.distinct()
    val latestCompatibleVersionCode: Long? get() = compatibleVersions.firstOrNull()
    val suggestedUpdateVersionCode: Long? get() = latestCompatibleVersionCode
        ?.takeIf { installedVersionCode == null || it > installedVersionCode }
    val hasUnresolvedNewerVersion: Boolean get() = assessments.any {
        it.channelAllowed && it.needsMetadata && it.incompatibilities.isEmpty() &&
            (latestCompatibleVersionCode == null || it.entry.artifact.versionCode > latestCompatibleVersionCode!!)
    }

    /** Keep variants separate. Source preference never skips package/version/compatibility gates. */
    fun sourcesFor(versionCode: Long): List<CatalogEntry> = assessments
        .filter { it.compatibleByMetadata && it.entry.artifact.versionCode == versionCode }
        .map { it.entry }.filter { it.artifact.downloadMode != DownloadMode.Unavailable }
        .sortedBy { SOURCE_ORDER.indexOf(it.artifact.source) }

    private companion object {
        val SOURCE_ORDER = listOf(Source.GooglePlay, Source.ApkMirror, Source.ApkPure)
    }
}

class VersionCatalog(private val checker: CompatibilityChecker = BaseCompatibilityChecker()) {
    fun select(
        packageName: String,
        entries: List<CatalogEntry>,
        device: GenericDeviceProfile,
        installedVersionCode: Long? = null,
        unavailableSources: Set<Source> = emptySet(),
        includePreviews: Boolean = false,
    ): VersionSelection {
        require(packageName.isNotBlank())
        require(installedVersionCode == null || installedVersionCode >= 0)
        require(entries.all { it.artifact.packageName == packageName && it.artifact.versionCode > 0 }) {
            "El catálogo contiene otro paquete o un código de versión inválido."
        }
        val assessments = entries.distinct().sortedByDescending { it.artifact.versionCode }.map { entry ->
            val artifact = entry.artifact
            val result = checker.check(artifact, device)
            var reasons = (result as? CompatibilityResult.Incompatible)?.reasons.orEmpty()
            // A missing target in partial provider metadata is unknown, not a targetSdk of 1.
            if (!entry.constraintsKnown && artifact.targetSdk == null) reasons = reasons - IncompatibilityReason.TargetSdk
            CatalogAssessment(entry, reasons, !entry.constraintsKnown || artifact.minSdk == null,
                includePreviews || entry.channel != ReleaseChannel.Preview)
        }
        return VersionSelection(assessments, unavailableSources, installedVersionCode)
    }
}
