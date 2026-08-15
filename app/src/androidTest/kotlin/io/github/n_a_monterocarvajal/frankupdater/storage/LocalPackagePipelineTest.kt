package io.github.n_a_monterocarvajal.frankupdater.storage

import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.n_a_monterocarvajal.frankupdater.archive.ArchiveFormat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPackagePipelineTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val fixtureDirectory = File(context.filesDir, "packages/library")

    @After
    fun cleanUp() {
        fixtureDirectory.listFiles { file -> file.name.startsWith("pipeline-test-") }
            .orEmpty()
            .forEach(File::delete)
    }

    @Test
    fun importsAndVerifiesApkApksApkmAndXapkThroughContentUris() = runBlocking {
        fixtureDirectory.mkdirs()
        val installedApk = File(context.applicationInfo.sourceDir)
        val fixtures = ArchiveFormat.entries.associateWith { format ->
            val file = File(fixtureDirectory, "pipeline-test-package.${format.extension}")
            if (format == ArchiveFormat.APK) {
                installedApk.copyTo(file, overwrite = true)
            } else {
                ZipOutputStream(file.outputStream()).use { output ->
                    output.putNextEntry(ZipEntry("base.apk"))
                    installedApk.inputStream().use { it.copyTo(output) }
                    output.closeEntry()
                    if (format == ArchiveFormat.XAPK) {
                        output.putNextEntry(ZipEntry("Android/obb/$PACKAGE/main.1.$PACKAGE.obb"))
                        output.write("fixture".toByteArray())
                        output.closeEntry()
                    }
                }
            }
            file
        }
        val pipeline = LocalPackagePipeline(context)

        fixtures.forEach { (format, file) ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val prepared = pipeline.import(uri)
            try {
                assertEquals(format, prepared.verified.format)
                assertEquals(context.packageName, prepared.verified.packageName)
                assertTrue(prepared.verified.apks.single().signatureVerified)
                assertEquals(if (format == ArchiveFormat.XAPK) 1 else 0, prepared.verified.expansionFiles.size)
            } finally {
                prepared.close()
            }
        }
    }

    private companion object {
        const val PACKAGE = "io.github.n_a_monterocarvajal.frankupdater"
    }
}
