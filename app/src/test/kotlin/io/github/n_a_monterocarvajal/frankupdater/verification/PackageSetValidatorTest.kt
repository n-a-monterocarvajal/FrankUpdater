package io.github.n_a_monterocarvajal.frankupdater.verification

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PackageSetValidatorTest {
    private val validator = PackageSetValidator()

    @Test
    fun `accepts base and splits with the same identity`() {
        val apks = listOf(apk(), apk(split = "config.es"), apk(split = "config.xhdpi"))

        assertEquals(InstallAction.Install, validator.validate(apks))
    }

    @Test
    fun `rejects a split from a different package`() {
        val error = assertThrows(PackageVerificationException::class.java) {
            validator.validate(listOf(apk(), apk(split = "config.es", packageName = "other")))
        }

        assertEquals(setOf(VerificationIssue.PackageMismatch), error.issues)
    }

    @Test
    fun `rejects invalid signatures and signer disagreement`() {
        val error = assertThrows(PackageVerificationException::class.java) {
            validator.validate(
                listOf(
                    apk(),
                    apk(split = "feature", signers = setOf("other"), signatureVerified = false),
                ),
            )
        }

        assertEquals(
            setOf(VerificationIssue.InvalidSignature, VerificationIssue.SignerMismatch),
            error.issues,
        )
    }

    @Test
    fun `accepts a rotated signer whose verified lineage contains installed signer`() {
        val installed = InstalledPackageIdentity("example", 1, setOf("old"))
        val candidate = apk(versionCode = 2, signers = setOf("new"), lineage = setOf("old", "new"))
            .copy(authorizedUpdateSigners = setOf("old"))

        assertEquals(InstallAction.Update, validator.validate(listOf(candidate), installed = installed))
    }

    @Test
    fun `rejects an unrelated signer and a downgrade`() {
        val installed = InstalledPackageIdentity("example", 3, setOf("installed"))
        val error = assertThrows(PackageVerificationException::class.java) {
            validator.validate(listOf(apk(versionCode = 2)), installed = installed)
        }

        assertEquals(
            setOf(VerificationIssue.Downgrade, VerificationIssue.InstalledSignerMismatch),
            error.issues,
        )
    }

    @Test
    fun `classifies equal version as explicit reinstall`() {
        val installed = InstalledPackageIdentity("example", 2, setOf("signer"))

        assertEquals(
            InstallAction.Reinstall,
            validator.validate(listOf(apk(versionCode = 2)), installed = installed),
        )
    }

    @Test
    fun `lineage alone and empty or partial installed signers cannot authorize update`() {
        val candidate = apk(signers = setOf("new"), lineage = setOf("old", "new"))
        for (installedSigners in listOf(emptySet(), setOf("old"), setOf("old", "new"))) {
            val error = assertThrows(PackageVerificationException::class.java) {
                validator.validate(listOf(candidate), installed = InstalledPackageIdentity("example", 1, installedSigners))
            }
            assertEquals(setOf(VerificationIssue.InstalledSignerMismatch), error.issues)
        }
    }

    @Test
    fun `duplicate split names are rejected before installation`() {
        val error = assertThrows(PackageVerificationException::class.java) {
            validator.validate(listOf(apk(), apk(split = "config.es"), apk(split = "config.es")))
        }
        assertEquals(setOf(VerificationIssue.DuplicateSplit), error.issues)
    }

    private fun apk(
        split: String? = null,
        packageName: String = "example",
        versionCode: Long = 2,
        signers: Set<String> = setOf("signer"),
        lineage: Set<String> = signers,
        signatureVerified: Boolean = true,
    ) = ParsedApk(
        entryName = split?.let { "$it.apk" } ?: "base.apk",
        file = File(split?.let { "$it.apk" } ?: "base.apk"),
        sha256 = "hash",
        packageName = packageName,
        versionName = "1.0",
        versionCode = versionCode,
        splitName = split,
        minSdk = 23,
        targetSdk = 37,
        signerDigests = signers,
        signingLineage = lineage,
        signatureVerified = signatureVerified,
        signatureErrors = if (signatureVerified) emptyList() else listOf("bad signature"),
    )
}
