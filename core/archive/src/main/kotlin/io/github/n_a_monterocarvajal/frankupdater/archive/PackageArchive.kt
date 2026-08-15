/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Archive concepts adapted from App Manager's ApkFile.java at
 * fc1e70074e8cf75c0619e526e688c16ad2e1e862. The implementation is original
 * Kotlin and deliberately keeps Android package parsing outside this module.
 */
package io.github.n_a_monterocarvajal.frankupdater.archive

import java.io.File

enum class ArchiveFormat(val extension: String) {
    APK("apk"),
    APKS("apks"),
    APKM("apkm"),
    XAPK("xapk");

    companion object {
        fun fromFileName(fileName: String): ArchiveFormat {
            val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
            return entries.firstOrNull { it.extension == extension }
                ?: throw UnsupportedArchiveException("Tipo de paquete no admitido: .$extension")
        }
    }
}

data class ExtractedApk(
    val entryName: String,
    val file: File,
    val sizeBytes: Long,
    val sha256: String,
)

data class ArchivedExpansionFile(
    val entryName: String,
    val sizeBytes: Long,
)

data class ExtractedPackageArchive(
    val format: ArchiveFormat,
    val sourceFile: File,
    val sourceSizeBytes: Long,
    val sourceSha256: String,
    val apks: List<ExtractedApk>,
    val expansionFiles: List<ArchivedExpansionFile>,
)

open class PackageArchiveException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class UnsupportedArchiveException(message: String) : PackageArchiveException(message)

class InvalidArchiveException(message: String, cause: Throwable? = null) :
    PackageArchiveException(message, cause)

class ArchiveLimitException(message: String) : PackageArchiveException(message)

class IntegrityMismatchException(
    expected: String,
    actual: String,
) : PackageArchiveException("SHA-256 no coincide: se esperaba $expected y se obtuvo $actual")
