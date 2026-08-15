package io.github.n_a_monterocarvajal.frankupdater.archive

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PackageArchiveExtractorTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("frank-archive-test").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `reads a standalone apk and checks expected hash`() {
        val apk = File(root, "sample.apk").apply { writeBytes("signed apk".toByteArray()) }

        val result = PackageArchiveExtractor().extract(
            sourceFile = apk,
            displayName = apk.name,
            outputDirectory = File(root, "out"),
            expectedSha256 = apk.sha256().uppercase(),
        )

        assertEquals(ArchiveFormat.APK, result.format)
        assertEquals(apk, result.apks.single().file)
        assertEquals(apk.sha256(), result.sourceSha256)
    }

    @Test
    fun `reads every supported zip container and reports expansion files`() {
        listOf("apks", "apkm", "xapk").forEach { extension ->
            val archive = File(root, "sample.$extension")
            zip(
                archive,
                "base.apk" to "base".toByteArray(),
                "splits/config.es.apk" to "locale".toByteArray(),
                "Android/obb/example/main.1.example.obb" to "obb".toByteArray(),
            )

            val result = PackageArchiveExtractor().extract(
                archive,
                archive.name,
                File(root, "out-$extension"),
            )

            assertEquals(2, result.apks.size)
            assertEquals(1, result.expansionFiles.size)
            assertTrue(result.apks.all { it.file.isFile })
        }
    }

    @Test
    fun `rejects an expected hash mismatch`() {
        val apk = File(root, "sample.apk").apply { writeText("content") }

        assertThrows(IntegrityMismatchException::class.java) {
            PackageArchiveExtractor().extract(apk, apk.name, File(root, "out"), "00")
        }
    }

    @Test
    fun `rejects unsafe paths before extracting`() {
        val archive = File(root, "unsafe.apks")
        zip(archive, "../base.apk" to "base".toByteArray())

        assertThrows(InvalidArchiveException::class.java) {
            PackageArchiveExtractor().extract(archive, archive.name, File(root, "out"))
        }
        assertTrue(!File(root, "base.apk").exists())
    }

    @Test
    fun `enforces extracted size limit using actual bytes`() {
        val archive = File(root, "large.xapk")
        zip(archive, "base.apk" to ByteArray(32))
        val extractor = PackageArchiveExtractor(
            ArchiveLimits(maxEntries = 5, maxApkBytes = 16, maxTotalExtractedBytes = 16),
        )

        assertThrows(ArchiveLimitException::class.java) {
            extractor.extract(archive, archive.name, File(root, "out"))
        }
    }

    @Test
    fun `rejects a container without apks`() {
        val archive = File(root, "empty.apkm")
        zip(archive, "info.json" to "{}".toByteArray())

        assertThrows(InvalidArchiveException::class.java) {
            PackageArchiveExtractor().extract(archive, archive.name, File(root, "out"))
        }
    }

    private fun zip(file: File, vararg entries: Pair<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, bytes) ->
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
    }
}
