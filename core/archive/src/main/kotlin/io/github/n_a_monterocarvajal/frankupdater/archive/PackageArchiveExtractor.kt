/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Adapted conceptually from App Manager ApkFile.java, GPL-3.0-or-later,
 * revision fc1e70074e8cf75c0619e526e688c16ad2e1e862.
 */
package io.github.n_a_monterocarvajal.frankupdater.archive

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipFile

data class ArchiveLimits(
    val maxEntries: Int = 512,
    val maxApkBytes: Long = 1_610_612_736L,
    val maxTotalExtractedBytes: Long = 3_221_225_472L,
)

class PackageArchiveExtractor(
    private val limits: ArchiveLimits = ArchiveLimits(),
) {
    fun extract(
        sourceFile: File,
        displayName: String,
        outputDirectory: File,
        expectedSha256: String? = null,
    ): ExtractedPackageArchive {
        require(sourceFile.isFile) { "El paquete de origen no existe." }
        val format = ArchiveFormat.fromFileName(displayName)
        val sourceSha256 = sourceFile.sha256()
        if (expectedSha256 != null && !sourceSha256.equals(expectedSha256, ignoreCase = true)) {
            throw IntegrityMismatchException(expectedSha256.lowercase(), sourceSha256)
        }
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw IOException("No se pudo crear el directorio temporal de lectura.")
        }

        val apks = if (format == ArchiveFormat.APK) {
            listOf(
                ExtractedApk(
                    entryName = displayName,
                    file = sourceFile,
                    sizeBytes = sourceFile.length(),
                    sha256 = sourceSha256,
                ),
            )
        } else {
            extractZip(sourceFile, format, outputDirectory)
        }

        val expansionFiles = if (format == ArchiveFormat.APK) {
            emptyList()
        } else {
            readExpansionFiles(sourceFile)
        }

        return ExtractedPackageArchive(
            format = format,
            sourceFile = sourceFile,
            sourceSizeBytes = sourceFile.length(),
            sourceSha256 = sourceSha256,
            apks = apks,
            expansionFiles = expansionFiles,
        )
    }

    private fun extractZip(
        sourceFile: File,
        format: ArchiveFormat,
        outputDirectory: File,
    ): List<ExtractedApk> {
        val extracted = mutableListOf<ExtractedApk>()
        var totalExtracted = 0L
        try {
            ZipFile(sourceFile).use { zip ->
                val entries = zip.entries().asSequence().toList()
                if (entries.size > limits.maxEntries) {
                    throw ArchiveLimitException("El contenedor supera ${limits.maxEntries} entradas.")
                }
                entries.forEach(::validateEntryName)
                entries.filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                    .forEachIndexed { index, entry ->
                        if (entry.size > limits.maxApkBytes) {
                            throw ArchiveLimitException("${entry.name} supera el tamaño máximo permitido.")
                        }
                        val safeName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                        val target = File(outputDirectory, "%03d_%s".format(Locale.ROOT, index, safeName))
                        val digest = MessageDigest.getInstance("SHA-256")
                        var written = 0L
                        zip.getInputStream(entry).use { rawInput ->
                            BufferedInputStream(rawInput).use { input ->
                                BufferedOutputStream(FileOutputStream(target)).use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        written += count
                                        totalExtracted += count
                                        if (written > limits.maxApkBytes ||
                                            totalExtracted > limits.maxTotalExtractedBytes
                                        ) {
                                            throw ArchiveLimitException(
                                                "El contenido descomprimido supera el límite de seguridad.",
                                            )
                                        }
                                        digest.update(buffer, 0, count)
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                        }
                        extracted += ExtractedApk(
                            entryName = entry.name,
                            file = target,
                            sizeBytes = written,
                            sha256 = digest.digest().toHex(),
                        )
                    }
            }
        } catch (error: ArchiveLimitException) {
            throw error
        } catch (error: ZipException) {
            val detail = if (format == ArchiveFormat.APKM) {
                "APKM cifrado o contenedor ZIP inválido."
            } else {
                "Contenedor ${format.name} inválido."
            }
            throw InvalidArchiveException(detail, error)
        } catch (error: IOException) {
            throw InvalidArchiveException("No se pudo leer el contenedor ${format.name}.", error)
        }
        if (extracted.isEmpty()) {
            throw InvalidArchiveException("El contenedor no contiene ningún APK.")
        }
        return extracted
    }

    private fun readExpansionFiles(sourceFile: File): List<ArchivedExpansionFile> =
        try {
            ZipFile(sourceFile).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".obb", ignoreCase = true) }
                    .map { ArchivedExpansionFile(it.name, it.size) }
                    .toList()
            }
        } catch (error: IOException) {
            throw InvalidArchiveException("No se pudo comprobar el contenido adicional.", error)
        }

    private fun validateEntryName(entry: java.util.zip.ZipEntry) {
        val normalized = entry.name.replace('\\', '/')
        val parts = normalized.split('/')
        if (normalized.startsWith('/') ||
            Regex("^[A-Za-z]:").containsMatchIn(normalized) ||
            parts.any { it == ".." }
        ) {
            throw InvalidArchiveException("Ruta insegura dentro del contenedor: ${entry.name}")
        }
    }
}

fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    BufferedInputStream(FileInputStream(this)).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
