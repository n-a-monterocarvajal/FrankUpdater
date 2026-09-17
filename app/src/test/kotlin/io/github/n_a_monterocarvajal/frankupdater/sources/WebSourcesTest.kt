package io.github.n_a_monterocarvajal.frankupdater.sources

import io.github.n_a_monterocarvajal.frankupdater.model.*
import java.io.File
import java.nio.file.Files
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class WebSourcesTest {
    private val mirror = "https://www.apkmirror.com/apk/example/app/"

    @Test fun `mirror CDN is exact download only and receives no source cookies`() {
        val cdn = "https://eb5e7388c3df147b74dd2379b7cf8323.r2.cloudflarestorage.com/file.apk"
        sourceUrl(cdn, Source.ApkMirror, download = true)
        assertThrows(IllegalArgumentException::class.java) { sourceUrl(cdn, Source.ApkMirror) }
        assertThrows(IllegalArgumentException::class.java) { sourceUrl("https://other.r2.cloudflarestorage.com/file.apk", Source.ApkMirror, true) }
        val directory = Files.createTempDirectory("mirror-cdn-test").toFile()
        try {
            val client = WebSourceClient(OkHttpClient.Builder().addInterceptor { chain ->
                if (chain.request().url.host == "www.apkmirror.com") reply(chain.request(), 302).newBuilder().header("Location", cdn).build()
                else {
                    assertNull(chain.request().header("Cookie"))
                    reply(chain.request(), 200, "fixture")
                }
            }.build())
            client.download(mirror, Source.ApkMirror, File(directory, "download.apk"), mapOf("Cookie" to "fixture"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun `preview labels are not promoted to stable and release channel reaches variants`() {
        assertEquals(io.github.n_a_monterocarvajal.frankupdater.compatibility.ReleaseChannel.Unknown, releaseChannel("1.2.0"))
        val variants = MirrorParser.variants("<title>Calculator (Early Access)</title>" + fixture("mirror.html"), mirror)
        assertTrue(variants.all { it.channel == io.github.n_a_monterocarvajal.frankupdater.compatibility.ReleaseChannel.Preview })
    }

    @Test fun `mirror table port retains bundles ABIs and unresolved version codes`() {
        val html = fixture("mirror.html")
        assertEquals("https://www.apkmirror.com/apk/example/app/app-2-release/", MirrorParser.releases(html, mirror).single().url)
        val variants = MirrorParser.variants(html, mirror)
        assertEquals(3, variants.size)
        assertEquals(PackageType.Apkm, variants.first().type)
        assertEquals(100L, variants.first().versionCode)
        assertNull(variants[1].versionCode)
        assertEquals(10L, variants.last().versionCode)
        assertEquals(26, variants.last().catalogEntry("org.example.app")!!.artifact.minSdk)
        val candidate = variants.first().catalogEntry("org.example.app")!!
        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), candidate.artifact.abis)
        assertEquals(26, candidate.artifact.minSdk)
        assertFalse(candidate.constraintsKnown)
        assertEquals("https://www.apkmirror.com/download/?key=fixture", MirrorParser.downloadPage(html, mirror))
        assertEquals("https://www.apkmirror.com/wp-content/file.apk", MirrorParser.downloadUrl(html, mirror))
    }

    @Test fun `changed markup challenge and hostile links never become valid results`() {
        for (html in listOf("<html>Changed layout</html>", "Enable JavaScript and cookies to continue")) {
            assertThrows(IllegalArgumentException::class.java) { MirrorParser.releases(html, mirror) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            MirrorParser.downloadPage("<a class=downloadButton href='https://evil.invalid/a.apk'>download</a>", mirror)
        }
        for (url in listOf("http://apkmirror.com/a", "https://apkmirror.com.evil.invalid/a",
            "https://user:secret@apkmirror.com/a", "https://apkmirror.com:444/a")) {
            assertThrows(IllegalArgumentException::class.java) { sourceUrl(url, Source.ApkMirror) }
        }
    }

    @Test fun `pure history sorts codes retains ABI variants and rejects unrelated or unsafe assets`() {
        val entries = PureParser.history(fixture("pure.json"), "org.example.app")
        assertEquals(listOf(100L, 99L, 99L), entries.map { it.artifact.versionCode })
        assertEquals(PackageType.Xapk, entries.first().artifact.packageType)
        assertEquals(listOf("x86"), entries.last().artifact.abis)
        assertTrue(entries.all { !it.constraintsKnown })
        assertThrows(IllegalArgumentException::class.java) { PureParser.history("{\"version_list\":[]}", "org.example.app") }
    }

    @Test fun `redirects drop source headers and reject foreign domains before requesting them`() {
        var calls = 0
        val client = WebSourceClient(OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            val request = chain.request()
            if (calls == 1) {
                assertEquals("fixture", request.header("Cookie"))
                reply(request, 302).newBuilder().header("Location", "https://www.apkmirror.com/next").build()
            } else {
                assertNull(request.header("Cookie"))
                reply(request, 302).newBuilder().header("Location", "https://evil.invalid/file").build()
            }
        }.build())
        assertThrows(IllegalArgumentException::class.java) { client.text(mirror, Source.ApkMirror, mapOf("Cookie" to "fixture")) }
        assertEquals(2, calls)
    }

    @Test fun `download writes privately and deletes failed files`() {
        val directory = Files.createTempDirectory("web-download-test").toFile()
        try {
            val file = File(directory, "download.apk")
            val client = WebSourceClient(OkHttpClient.Builder().addInterceptor { reply(it.request(), 200, "fixture bytes") }.build())
            assertEquals("fixture bytes", client.download(mirror, Source.ApkMirror, file).readText())
            file.delete()
            assertThrows(IllegalArgumentException::class.java) {
                client.download(mirror, Source.ApkMirror, file, expectedSha256 = "0".repeat(64))
            }
            assertFalse(file.exists())
            assertThrows(IllegalArgumentException::class.java) {
                client.download(mirror, Source.ApkMirror, file, expectedSize = 2)
            }
            assertFalse(file.exists())
            val blocked = WebSourceClient(OkHttpClient.Builder().addInterceptor { reply(it.request(), 403) }.build())
            assertThrows(java.io.IOException::class.java) { blocked.download(mirror, Source.ApkMirror, file) }
            assertFalse(file.exists())
        } finally { directory.deleteRecursively() }
    }

    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/sources/$name")).readText()
    private fun reply(request: Request, code: Int, body: String = "") = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body.toResponseBody()).build()
}
