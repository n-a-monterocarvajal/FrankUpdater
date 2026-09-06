/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.verification

import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest

internal data class VerifiedSignatures(
    val signers: Set<String>,
    val lineage: Set<String>,
    val authorizedAncestors: Set<String>,
)

internal class SignatureVerifier {
    fun inspect(apk: File, sdk: Int): VerifiedSignatures {
        require(sdk >= 23)
        val result = ApkVerifier.Builder(apk)
            .setMinCheckedPlatformVersion(sdk)
            .setMaxCheckedPlatformVersion(sdk)
            .build().verify()
        require(result.isVerified) { "La firma del APK no es válida para este Android." }
        val signers = result.signerCertificates.map { digest(it.encoded) }.toSet()
        require(signers.isNotEmpty()) { "El APK no tiene firmantes verificables." }
        val lineage = result.signingCertificateLineage
        val certificates = lineage?.certificatesInLineage.orEmpty()
        val currentIndex = certificates.indexOfFirst { digest(it.encoded) in signers }
        val authorized = if (sdk >= 28 && signers.size == 1 && currentIndex > 0) {
            certificates.take(currentIndex)
                .filter { requireNotNull(lineage).getSignerCapabilities(it).hasInstalledData() }
                .map { digest(it.encoded) }.toSet()
        } else emptySet()
        return VerifiedSignatures(signers, certificates.map { digest(it.encoded) }.toSet(), authorized)
    }

    fun verify(apk: File, sdk: Int, installedSigners: Set<String>? = null): Set<String> {
        val result = inspect(apk, sdk)
        require(installedSigners == null ||
            result.signers == installedSigners ||
            (installedSigners.size == 1 && result.signers.size == 1 &&
                installedSigners.single() in result.authorizedAncestors)) {
            "La firma no puede actualizar la aplicación instalada."
        }
        return result.signers
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
