/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.n_a_monterocarvajal.frankupdater.verification

import io.github.n_a_monterocarvajal.frankupdater.archive.ArchiveFormat
import io.github.n_a_monterocarvajal.frankupdater.archive.ArchivedExpansionFile
import java.io.File

enum class InstallAction {
    Install,
    Update,
    Reinstall,
}

data class ParsedApk(
    val entryName: String,
    val file: File,
    val sha256: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val splitName: String?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val signerDigests: Set<String>,
    val signingLineage: Set<String>,
    val signatureVerified: Boolean,
    val signatureErrors: List<String>,
) {
    val isBase: Boolean
        get() = splitName == null
}

data class InstalledPackageIdentity(
    val packageName: String,
    val versionCode: Long,
    val currentSignerDigests: Set<String>,
)

data class VerifiedPackageArchive(
    val format: ArchiveFormat,
    val sourceFile: File,
    val sourceSha256: String,
    val sourceSizeBytes: Long,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int?,
    val targetSdk: Int?,
    val signerDigests: Set<String>,
    val signingLineage: Set<String>,
    val apks: List<ParsedApk>,
    val expansionFiles: List<ArchivedExpansionFile>,
    val installAction: InstallAction,
)

enum class VerificationIssue {
    InvalidSignature,
    MissingBase,
    MultipleBases,
    PackageMismatch,
    VersionMismatch,
    SignerMismatch,
    ExpectedPackageMismatch,
    ExpectedVersionMismatch,
    InstalledSignerMismatch,
    Downgrade,
}

class PackageVerificationException(
    val issues: Set<VerificationIssue>,
    message: String,
) : Exception(message)

data class VerificationExpectations(
    val packageName: String? = null,
    val versionCode: Long? = null,
)
