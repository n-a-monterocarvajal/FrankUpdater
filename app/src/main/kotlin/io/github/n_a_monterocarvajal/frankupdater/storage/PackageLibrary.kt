/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.n_a_monterocarvajal.frankupdater.storage

import android.content.Context
import io.github.n_a_monterocarvajal.frankupdater.archive.ArchiveFormat
import io.github.n_a_monterocarvajal.frankupdater.verification.VerifiedPackageArchive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

enum class PackageRetentionPolicy {
    DeleteAutomatically,
    Ask,
    KeepAlways,
}

class RetentionPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "package_retention",
        Context.MODE_PRIVATE,
    )

    var policy: PackageRetentionPolicy
        get() = runCatching {
            PackageRetentionPolicy.valueOf(
                preferences.getString(KEY_POLICY, PackageRetentionPolicy.Ask.name)!!,
            )
        }.getOrDefault(PackageRetentionPolicy.Ask)
        set(value) {
            preferences.edit().putString(KEY_POLICY, value.name).apply()
        }

    private companion object {
        const val KEY_POLICY = "policy"
    }
}

data class PackageLibraryEntry(
    val id: String,
    val originalName: String,
    val format: ArchiveFormat,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val sourceSha256: String,
    val signerDigests: Set<String>,
    val importedAtEpochMillis: Long,
    val sizeBytes: Long,
    val apkCount: Int,
    val expansionFileCount: Int,
    val sourceUri: String,
    val importMethod: String,
    val archiveFile: File,
)

internal object LibraryMetadataCodec {
    fun write(entry: PackageLibraryEntry, destination: File) {
        val properties = Properties().apply {
            setProperty("schema", "1")
            setProperty("id", entry.id)
            setProperty("originalName", entry.originalName)
            setProperty("format", entry.format.name)
            setProperty("packageName", entry.packageName)
            setProperty("versionName", entry.versionName.orEmpty())
            setProperty("versionCode", entry.versionCode.toString())
            setProperty("sourceSha256", entry.sourceSha256)
            setProperty("signerDigests", entry.signerDigests.sorted().joinToString(","))
            setProperty("importedAtEpochMillis", entry.importedAtEpochMillis.toString())
            setProperty("sizeBytes", entry.sizeBytes.toString())
            setProperty("apkCount", entry.apkCount.toString())
            setProperty("expansionFileCount", entry.expansionFileCount.toString())
            setProperty("sourceUri", entry.sourceUri)
            setProperty("importMethod", entry.importMethod)
            setProperty("archiveFileName", entry.archiveFile.name)
        }
        FileOutputStream(destination).use { properties.store(it, null) }
    }

    fun read(source: File, libraryDirectory: File): PackageLibraryEntry {
        val properties = Properties().apply {
            FileInputStream(source).use(::load)
        }
        require(properties.getProperty("schema") == "1") { "Versión de metadata desconocida." }
        val archiveFile = File(libraryDirectory, properties.required("archiveFileName")).canonicalFile
        require(archiveFile.parentFile == libraryDirectory.canonicalFile) {
            "Ruta de paquete conservado inválida."
        }
        return PackageLibraryEntry(
            id = properties.required("id"),
            originalName = properties.required("originalName"),
            format = ArchiveFormat.valueOf(properties.required("format")),
            packageName = properties.required("packageName"),
            versionName = properties.required("versionName").ifBlank { null },
            versionCode = properties.required("versionCode").toLong(),
            sourceSha256 = properties.required("sourceSha256"),
            signerDigests = properties.required("signerDigests")
                .split(',')
                .filter(String::isNotBlank)
                .toSet(),
            importedAtEpochMillis = properties.required("importedAtEpochMillis").toLong(),
            sizeBytes = properties.required("sizeBytes").toLong(),
            apkCount = properties.required("apkCount").toInt(),
            expansionFileCount = properties.required("expansionFileCount").toInt(),
            sourceUri = properties.required("sourceUri"),
            importMethod = properties.required("importMethod"),
            archiveFile = archiveFile,
        )
    }

    private fun Properties.required(key: String): String =
        getProperty(key) ?: error("Falta $key en la metadata de biblioteca.")
}

class LocalPackageLibrary(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "packages/library")

    fun list(): List<PackageLibraryEntry> {
        if (!directory.exists()) return emptyList()
        return directory.listFiles { file -> file.extension == METADATA_EXTENSION }
            .orEmpty()
            .mapNotNull { metadata ->
                runCatching { LibraryMetadataCodec.read(metadata, directory) }
                    .getOrNull()
                    ?.takeIf { it.archiveFile.isFile }
            }
            .sortedByDescending(PackageLibraryEntry::importedAtEpochMillis)
    }

    fun retain(
        prepared: PreparedPackageImport,
        importedAtEpochMillis: Long = System.currentTimeMillis(),
    ): PackageLibraryEntry {
        if (!directory.exists() && !directory.mkdirs()) {
            error("No se pudo crear la biblioteca local.")
        }
        val verified = prepared.verified
        val id = verified.sourceSha256.take(24)
        val archiveName = "$id.${verified.format.extension}"
        val archive = File(directory, archiveName)
        if (!archive.exists()) {
            val partial = File(directory, "$archiveName.part")
            verified.sourceFile.inputStream().use { input ->
                partial.outputStream().use(input::copyTo)
            }
            check(partial.renameTo(archive)) { "No se pudo conservar el paquete importado." }
        }
        val entry = PackageLibraryEntry(
            id = id,
            originalName = prepared.originalName,
            format = verified.format,
            packageName = verified.packageName,
            versionName = verified.versionName,
            versionCode = verified.versionCode,
            sourceSha256 = verified.sourceSha256,
            signerDigests = verified.signerDigests,
            importedAtEpochMillis = importedAtEpochMillis,
            sizeBytes = verified.sourceSizeBytes,
            apkCount = verified.apks.size,
            expansionFileCount = verified.expansionFiles.size,
            sourceUri = prepared.sourceUri,
            importMethod = IMPORT_METHOD_SAF,
            archiveFile = archive,
        )
        val metadata = File(directory, "$id.$METADATA_EXTENSION")
        val partialMetadata = File(directory, "$id.$METADATA_EXTENSION.part")
        LibraryMetadataCodec.write(entry, partialMetadata)
        if (metadata.exists()) check(metadata.delete())
        check(partialMetadata.renameTo(metadata)) { "No se pudo guardar la metadata local." }
        return entry
    }

    fun delete(entry: PackageLibraryEntry): Boolean {
        val expectedMetadata = File(directory, "${entry.id}.$METADATA_EXTENSION").canonicalFile
        require(expectedMetadata.parentFile == directory.canonicalFile)
        val archiveDeleted = !entry.archiveFile.exists() || entry.archiveFile.delete()
        val metadataDeleted = !expectedMetadata.exists() || expectedMetadata.delete()
        return archiveDeleted && metadataDeleted
    }

    private companion object {
        const val METADATA_EXTENSION = "properties"
        const val IMPORT_METHOD_SAF = "imported-saf"
    }
}

data class PreparedPackageImport(
    val verified: VerifiedPackageArchive,
    val originalName: String,
    val sourceUri: String,
    internal val workingDirectory: File,
) : AutoCloseable {
    override fun close() {
        workingDirectory.deleteRecursively()
        if (verified.sourceFile.parentFile?.name == "staging") {
            verified.sourceFile.delete()
        }
    }
}
