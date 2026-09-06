package io.github.n_a_monterocarvajal.frankupdater.play

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackageLibrary
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackagePipeline
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in only: normal test runs never contact a dispenser or Google Play. */
@RunWith(AndroidJUnit4::class)
class PlayLiveTest {
    @Test fun anonymousSearchDownloadAndVerify() {
        val arguments = InstrumentationRegistry.getArguments()
        val endpoint = arguments.getString("playDispenser").orEmpty()
        assumeTrue("Live Play test requires explicit playDispenser argument", endpoint.isNotBlank())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val http = PlayHttpClient()
        var stage = "inicio"
        fun progress(value: String) {
            stage = value
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nPlay: $value\n") })
        }
        val directory = File(context.cacheDir, "play-live/${UUID.randomUUID()}")
        try {
            runBlocking {
                progress("acceso anónimo")
                val provider = PlayProvider.anonymous(endpoint, playDeviceProperties(context), Locale.US, http)
                progress("búsqueda")
                val results = provider.search("Fossify Calculator")
                assertTrue("Empty search result", results.isNotEmpty())
                progress("detalles")
                val app = provider.details("org.fossify.math")
                assertEquals("org.fossify.math", app.packageName)
                assertTrue(app.versionCode > 0 && app.isFree)
                progress("delivery")
                val files = provider.delivery(app, app.versionCode)
                assertTrue("Live fixture exceeds 25 MiB limit", files.sumOf { it.size } <= 25 * 1024 * 1024)
                progress("descarga")
                val archive = PlayDownload().download(files, directory)
                progress("verificación y biblioteca")
                LocalPackagePipeline(context).importPlayDownload(archive, app.packageName, app.versionCode).use { prepared ->
                    assertEquals(files.size, prepared.verified.apks.size)
                    val library = LocalPackageLibrary(context)
                    val existing = library.list().any { it.sourceSha256 == prepared.verified.sourceSha256 }
                    if (!existing) {
                        val entry = library.retain(prepared)
                        try {
                            assertEquals("direct-play", entry.importMethod)
                            assertTrue(library.list().any { it.id == entry.id })
                        } finally { check(library.delete(entry)) }
                    }
                    progress("OK: ${app.packageName}, versión ${app.versionCode}, ${files.size} APK")
                }
            }
        } catch (error: Throwable) {
            // Do not attach the exception/cause: upstream errors may contain account data.
            throw AssertionError("Play falló en $stage: ${error.javaClass.simpleName}; HTTP ${http.responseCode.value}.")
        } finally { directory.deleteRecursively() }
    }
}
