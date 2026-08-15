/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Signature verification delegates to apksig-android 4.4.0 at
 * c120428b7b07f2a2b638c7519c2d60680d6398ce (Apache-2.0).
 */
package io.github.n_a_monterocarvajal.frankupdater.verification

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.android.apksig.ApkVerifier
import io.github.n_a_monterocarvajal.frankupdater.archive.ExtractedPackageArchive
import java.security.MessageDigest
import java.security.cert.Certificate

class AndroidPackageVerifier(
    context: Context,
    private val validator: PackageSetValidator = PackageSetValidator(),
) {
    private val packageManager = context.applicationContext.packageManager

    fun verify(
        archive: ExtractedPackageArchive,
        expectations: VerificationExpectations = VerificationExpectations(),
    ): VerifiedPackageArchive {
        val parsed = archive.apks.map { extracted ->
            val packageInfo = parsePackageInfo(extracted.file.absolutePath)
                ?: throw PackageVerificationException(
                    setOf(VerificationIssue.PackageMismatch),
                    "Android no pudo leer ${extracted.entryName} como APK.",
                )
            val signatureResult = runCatching {
                ApkVerifier.Builder(extracted.file).build().verify()
            }.getOrElse { error ->
                throw PackageVerificationException(
                    setOf(VerificationIssue.InvalidSignature),
                    "No se pudo verificar la firma de ${extracted.entryName}: ${error.message}",
                )
            }
            val signerDigests = signatureResult.signerCertificates
                .map(Certificate::sha256)
                .toSet()
            val lineageDigests = signatureResult.signingCertificateLineage
                ?.certificatesInLineage
                ?.map(Certificate::sha256)
                ?.toSet()
                .orEmpty()
            val applicationInfo = packageInfo.applicationInfo
            ParsedApk(
                entryName = extracted.entryName,
                file = extracted.file,
                sha256 = extracted.sha256,
                packageName = packageInfo.packageName,
                versionName = packageInfo.versionName,
                versionCode = packageInfo.versionCodeCompat(),
                splitName = packageInfo.splitNames.orEmpty().singleOrNull(),
                minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    applicationInfo?.minSdkVersion
                } else {
                    null
                },
                targetSdk = applicationInfo?.targetSdkVersion,
                signerDigests = signerDigests,
                signingLineage = lineageDigests + signerDigests,
                signatureVerified = signatureResult.isVerified,
                signatureErrors = signatureResult.errors.map(Any::toString),
            )
        }
        val base = parsed.singleOrNull(ParsedApk::isBase) ?: parsed.first()
        val installed = installedIdentity(base.packageName)
        val action = validator.validate(parsed, expectations, installed)
        return VerifiedPackageArchive(
            format = archive.format,
            sourceFile = archive.sourceFile,
            sourceSha256 = archive.sourceSha256,
            sourceSizeBytes = archive.sourceSizeBytes,
            packageName = base.packageName,
            versionName = base.versionName,
            versionCode = base.versionCode,
            minSdk = base.minSdk,
            targetSdk = base.targetSdk,
            signerDigests = base.signerDigests,
            signingLineage = base.signingLineage,
            apks = parsed.sortedBy { if (it.isBase) 0 else 1 },
            expansionFiles = archive.expansionFiles,
            installAction = action,
        )
    }

    @Suppress("DEPRECATION")
    private fun parsePackageInfo(path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))
        } else {
            packageManager.getPackageArchiveInfo(path, 0)
        }

    @Suppress("DEPRECATION")
    private fun installedIdentity(packageName: String): InstalledPackageIdentity? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        } else {
            PackageManager.GET_SIGNATURES.toLong()
        }
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags))
            } else {
                packageManager.getPackageInfo(packageName, flags.toInt())
            }
        }.getOrNull() ?: return null
        val currentSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return InstalledPackageIdentity(
            packageName = packageName,
            versionCode = info.versionCodeCompat(),
            currentSignerDigests = currentSignatures
                ?.map { it.toByteArray().sha256() }
                ?.toSet()
                .orEmpty(),
        )
    }
}

@Suppress("DEPRECATION")
private fun PackageInfo.versionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

private fun Certificate.sha256(): String = encoded.sha256()

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString(separator = "") { byte -> "%02x".format(byte) }
