/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.n_a_monterocarvajal.frankupdater.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.n_a_monterocarvajal.frankupdater.archive.PackageArchiveExtractor
import io.github.n_a_monterocarvajal.frankupdater.verification.AndroidPackageVerifier
import io.github.n_a_monterocarvajal.frankupdater.verification.VerificationExpectations
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalPackagePipeline(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext
    private val extractor = PackageArchiveExtractor()
    private val verifier = AndroidPackageVerifier(appContext)

    internal suspend fun importPlayDownload(
        source: File,
        packageName: String,
        versionCode: Long,
    ): PreparedPackageImport = withContext(ioDispatcher) {
        val working = File(appContext.cacheDir, "package-read/${UUID.randomUUID()}")
        try {
            val extracted = extractor.extract(source, "download.apks", working)
            PreparedPackageImport(
                verified = verifier.verify(extracted, VerificationExpectations(packageName, versionCode)),
                originalName = "$packageName-$versionCode.apks",
                sourceUri = "https://play.google.com/store/apps/details?id=$packageName",
                workingDirectory = working,
                importMethod = "direct-play",
            )
        } catch (error: Exception) {
            working.deleteRecursively()
            throw error
        }
    }

    suspend fun import(
        uri: Uri,
        expectations: VerificationExpectations = VerificationExpectations(),
    ): PreparedPackageImport = withContext(ioDispatcher) {
        val displayName = queryDisplayName(uri)
        val stagingDirectory = File(appContext.filesDir, "packages/staging")
        check(stagingDirectory.exists() || stagingDirectory.mkdirs())
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
        val staging = File(stagingDirectory, "${UUID.randomUUID()}.$extension")
        val workingDirectory = File(appContext.cacheDir, "package-read/${UUID.randomUUID()}")
        try {
            copyDocument(uri, staging)
            val extracted = extractor.extract(
                sourceFile = staging,
                displayName = displayName,
                outputDirectory = workingDirectory,
            )
            val verified = verifier.verify(extracted, expectations)
            PreparedPackageImport(
                verified = verified,
                originalName = displayName,
                sourceUri = uri.toString(),
                workingDirectory = workingDirectory,
            )
        } catch (error: Exception) {
            workingDirectory.deleteRecursively()
            staging.delete()
            throw error
        }
    }

    suspend fun prepare(entry: PackageLibraryEntry): PreparedPackageImport = withContext(ioDispatcher) {
        val workingDirectory = File(appContext.cacheDir, "package-read/${UUID.randomUUID()}")
        try {
            val extracted = extractor.extract(
                sourceFile = entry.archiveFile,
                displayName = entry.originalName,
                outputDirectory = workingDirectory,
                expectedSha256 = entry.sourceSha256,
            )
            val verified = verifier.verify(
                extracted,
                VerificationExpectations(entry.packageName, entry.versionCode),
            )
            PreparedPackageImport(
                verified = verified,
                originalName = entry.originalName,
                sourceUri = entry.sourceUri,
                workingDirectory = workingDirectory,
            )
        } catch (error: Exception) {
            workingDirectory.deleteRecursively()
            throw error
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        val name = appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return name?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: error("El documento seleccionado no tiene nombre.")
    }

    private fun copyDocument(uri: Uri, destination: File) {
        val available = destination.parentFile?.usableSpace ?: Long.MAX_VALUE
        val copyLimit = minOf(
            MAX_SOURCE_BYTES,
            (available - FREE_SPACE_RESERVE_BYTES).coerceAtLeast(0),
        )
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    check(copied <= copyLimit) {
                        "El paquete supera el límite de importación o el espacio disponible."
                    }
                    output.write(buffer, 0, count)
                }
            }
        } ?: error("No se pudo abrir el documento seleccionado.")
    }

    private companion object {
        const val MAX_SOURCE_BYTES = 4_294_967_296L
        const val FREE_SPACE_RESERVE_BYTES = 67_108_864L
    }
}
