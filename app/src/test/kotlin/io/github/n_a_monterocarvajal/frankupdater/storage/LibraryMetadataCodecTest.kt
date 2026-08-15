package io.github.n_a_monterocarvajal.frankupdater.storage

import io.github.n_a_monterocarvajal.frankupdater.archive.ArchiveFormat
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LibraryMetadataCodecTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("frank-library-test").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `round trips all retained package provenance`() {
        val archive = File(directory, "entry.apks").apply { writeText("archive") }
        val entry = PackageLibraryEntry(
            id = "entry",
            originalName = "Example.apks",
            format = ArchiveFormat.APKS,
            packageName = "org.example",
            versionName = "2.0",
            versionCode = 20,
            sourceSha256 = "abc",
            signerDigests = setOf("two", "one"),
            importedAtEpochMillis = 42,
            sizeBytes = 7,
            apkCount = 3,
            expansionFileCount = 1,
            sourceUri = "content://provider/document/1",
            importMethod = "imported-saf",
            archiveFile = archive,
        )
        val metadata = File(directory, "entry.properties")

        LibraryMetadataCodec.write(entry, metadata)

        assertEquals(entry, LibraryMetadataCodec.read(metadata, directory))
    }
}
