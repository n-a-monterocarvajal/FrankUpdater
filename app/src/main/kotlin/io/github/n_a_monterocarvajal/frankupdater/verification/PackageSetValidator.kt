/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.n_a_monterocarvajal.frankupdater.verification

class PackageSetValidator {
    fun validate(
        apks: List<ParsedApk>,
        expectations: VerificationExpectations = VerificationExpectations(),
        installed: InstalledPackageIdentity? = null,
    ): InstallAction {
        require(apks.isNotEmpty())
        val first = apks.first()
        val issues = buildSet {
            if (apks.any { !it.signatureVerified }) add(VerificationIssue.InvalidSignature)
            when (apks.count(ParsedApk::isBase)) {
                0 -> add(VerificationIssue.MissingBase)
                1 -> Unit
                else -> add(VerificationIssue.MultipleBases)
            }
            if (apks.any { it.packageName != first.packageName }) {
                add(VerificationIssue.PackageMismatch)
            }
            if (apks.any { it.versionCode != first.versionCode }) {
                add(VerificationIssue.VersionMismatch)
            }
            if (apks.any { it.signerDigests != first.signerDigests }) {
                add(VerificationIssue.SignerMismatch)
            }
            if (expectations.packageName != null && expectations.packageName != first.packageName) {
                add(VerificationIssue.ExpectedPackageMismatch)
            }
            if (expectations.versionCode != null && expectations.versionCode != first.versionCode) {
                add(VerificationIssue.ExpectedVersionMismatch)
            }
            if (installed != null && installed.packageName == first.packageName) {
                if (first.versionCode < installed.versionCode) {
                    add(VerificationIssue.Downgrade)
                }
                if (!isUpdateSignerCompatible(first, installed.currentSignerDigests)) {
                    add(VerificationIssue.InstalledSignerMismatch)
                }
            }
        }
        if (issues.isNotEmpty()) {
            val signatureDetails = apks.flatMap(ParsedApk::signatureErrors).distinct()
            val detail = (issues.map(Enum<*>::name) + signatureDetails).joinToString()
            throw PackageVerificationException(issues, "El paquete no superó la verificación: $detail")
        }
        return when {
            installed == null || installed.packageName != first.packageName -> InstallAction.Install
            first.versionCode > installed.versionCode -> InstallAction.Update
            else -> InstallAction.Reinstall
        }
    }

    private fun isUpdateSignerCompatible(
        candidate: ParsedApk,
        installedCurrentSigners: Set<String>,
    ): Boolean = candidate.signerDigests == installedCurrentSigners ||
        candidate.signingLineage.containsAll(installedCurrentSigners)
}
