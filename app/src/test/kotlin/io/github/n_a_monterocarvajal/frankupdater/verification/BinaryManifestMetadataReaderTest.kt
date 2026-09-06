package io.github.n_a_monterocarvajal.frankupdater.verification

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertThrows
import org.junit.Test

class BinaryManifestMetadataReaderTest {
    @Test fun `oversized manifest is rejected before parsing`() {
        val apk = Files.createTempFile("large-manifest", ".apk").toFile()
        try {
            ZipOutputStream(apk.outputStream()).use {
                it.putNextEntry(ZipEntry("AndroidManifest.xml"))
                it.write(ByteArray(2 * 1024 * 1024 + 1))
                it.closeEntry()
            }
            assertThrows(IllegalArgumentException::class.java) { BinaryManifestMetadataReader().read(apk) }
        } finally { apk.delete() }
    }
}
