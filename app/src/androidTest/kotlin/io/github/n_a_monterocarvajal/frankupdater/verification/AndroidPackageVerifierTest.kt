package io.github.n_a_monterocarvajal.frankupdater.verification

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.n_a_monterocarvajal.frankupdater.archive.PackageArchiveExtractor
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPackageVerifierTest {
    @Test
    fun readsBinaryIdentityFromBaseApk() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val metadata = BinaryManifestMetadataReader().read(File(context.applicationInfo.sourceDir))

        assertEquals(context.packageName, metadata.packageName)
        assertTrue(metadata.versionCode > 0)
        assertEquals(null, metadata.splitName)
    }

    @Test
    fun readsStandaloneSplitRejectedByPackageManagerArchiveParsing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageManager = context.packageManager
        val splitApplication = packageManager.getInstalledApplications(0)
            .firstOrNull { application ->
                application.splitSourceDirs.orEmpty().any { File(it).canRead() }
            }
        assumeNotNull(splitApplication)
        val splitPaths = splitApplication!!.splitSourceDirs.orEmpty()
        val splitPath = splitPaths.first { File(it).canRead() }

        val metadata = BinaryManifestMetadataReader().read(File(splitPath))

        assertEquals(splitApplication.packageName, metadata.packageName)
        assertTrue(metadata.versionCode > 0)
        assertNotNull(metadata.splitName)
        assertFalse(metadata.splitName.isNullOrBlank())
    }

    @Test
    fun verifiesInstalledDebugApkCryptographically() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sourceApk = File(context.applicationInfo.sourceDir)
        val archive = PackageArchiveExtractor().extract(
            sourceFile = sourceApk,
            displayName = "frankupdater.apk",
            outputDirectory = File(context.cacheDir, "verify-test"),
        )

        val verified = AndroidPackageVerifier(context).verify(archive)

        assertEquals(context.packageName, verified.packageName)
        assertEquals(InstallAction.Reinstall, verified.installAction)
        assertTrue(verified.signerDigests.isNotEmpty())
        assertTrue(verified.apks.single().signatureVerified)
    }
}
