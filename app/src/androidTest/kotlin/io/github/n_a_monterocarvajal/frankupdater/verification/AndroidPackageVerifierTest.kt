package io.github.n_a_monterocarvajal.frankupdater.verification

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.n_a_monterocarvajal.frankupdater.archive.PackageArchiveExtractor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPackageVerifierTest {
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
