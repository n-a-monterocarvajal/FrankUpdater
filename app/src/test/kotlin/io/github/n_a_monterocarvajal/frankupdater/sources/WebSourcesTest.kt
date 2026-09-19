package io.github.n_a_monterocarvajal.frankupdater.sources

import io.github.n_a_monterocarvajal.frankupdater.model.*
import java.io.File
import java.nio.file.Files
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
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
        for (label in listOf("F-Droid 2.0-alpha8", "App 3.1 beta2", "App 1.0-rc1", "Calculator (Early Access)")) {
            assertEquals(label, io.github.n_a_monterocarvajal.frankupdater.compatibility.ReleaseChannel.Preview, releaseChannel(label))
        }
        assertEquals(io.github.n_a_monterocarvajal.frankupdater.compatibility.ReleaseChannel.Unknown, releaseChannel("Alphabet Launcher 2.0"))
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

    @Test fun `variant page completes requirements and binds the file hash, not the certificate`() {
        val details = MirrorParser.variantDetails(fixture("mirror-variant.html"), mirror)
        assertEquals(MirrorVariantDetails(200, 26, 37, listOf("arm64-v8a", "x86_64"), 113_724_209,
            "a66ee23228d5c6d3a402083dbe9d97e8f7a2f3f38a0ffab51e547b1bf49d3f08", "org.example.app", "1".repeat(64)), details)
        val row = MirrorVariant("2.0", "arm64-v8a", "Android 8.0+", "nodpi", mirror, PackageType.MonolithicApk, null)
        assertNull(row.catalogEntry("org.example.app"))
        val entry = row.catalogEntry("org.example.app", details)!!
        assertTrue(entry.constraintsKnown)
        assertEquals(200L, entry.artifact.versionCode)
        assertEquals(37, entry.artifact.targetSdk)
        assertEquals(113_724_209L, entry.artifact.artifacts.single().sizeBytes)
        assertEquals(setOf("1".repeat(64)), entry.artifact.signerDigests)
        val universal = MirrorParser.variantDetails(fixture("mirror-variant.html")
            .replace("arm64-v8a + x86_64", "universal").replace("APK file hashes", "none"), mirror)
        assertEquals(emptyList<String>(), universal.abis)
        assertNull(universal.sha256)
    }

    @Test fun `F-Droid repositories list versions, mark unsuggested ones as previews and build repo URLs`() {
        val json = """{"packageName":"org.example.app","suggestedVersionCode":"10","packages":[
            {"versionName":"1.1-beta","versionCode":"11"},{"versionName":"1.0","versionCode":"10"}]}"""
        val entries = FdroidParser.history(json, "org.example.app", Source.IzzyOnDroid)
        assertEquals(listOf(11L, 10L), entries.map { it.artifact.versionCode })
        assertEquals(io.github.n_a_monterocarvajal.frankupdater.compatibility.ReleaseChannel.Preview, entries.first().channel)
        assertEquals("https://apt.izzysoft.de/fdroid/repo/org.example.app_10.apk", entries.last().artifact.artifacts.single().uri)
        assertFalse(entries.last().constraintsKnown)
        assertThrows(IllegalArgumentException::class.java) { FdroidParser.history(json, "other.app", Source.FDroid) }
    }

    @Test fun `mirror search keeps only release links`() {
        val html = """<div class="appRow"><h5 class="appRowTitle"><a href="/apk/dev/app/app-1-0-release/">App 1.0</a></h5>
            <a href="/apk/dev/">by Dev</a></div>
            <div class="appRow"><h5 class="appRowTitle"><a href="https://evil.invalid/x-release/">Evil</a></h5></div>"""
        assertEquals(listOf(WebRelease("App 1.0", "https://www.apkmirror.com/apk/dev/app/app-1-0-release/")),
            MirrorParser.searchReleases(html, mirror))
    }

    @Test fun `mirror feed lists release pages and release URLs map back to the app page`() {
        val xml = """<?xml version="1.0"?><rss><channel><link>https://www.apkmirror.com/apk/dev/app/</link>
            <item><title>App 2.0 by Dev</title><link>https://www.apkmirror.com/apk/dev/app/app-2-0-release/</link></item>
            <item><title>Foreign</title><link>https://evil.invalid/app-1-release/</link></item></channel></rss>"""
        assertEquals(listOf(WebRelease("App 2.0 by Dev", "https://www.apkmirror.com/apk/dev/app/app-2-0-release/")),
            MirrorParser.feedReleases(xml))
        assertEquals("https://www.apkmirror.com/apk/dev/app/", MirrorParser.appUrl("https://www.apkmirror.com/apk/dev/app/app-2-0-release/"))
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

    @Test fun `pure history confirms compatibility when sdk metadata is complete`() {
        val json = """{"version_list":[{"package_name":"org.example.app","version_code":"101","version_name":"v10.1","sdk_version":"23","target_sdk_version":"34","native_code":["arm64-v8a"],"asset":{"type":"APK","url":"https://download.apkpure.com/file-101.apk"}}]}"""
        val entry = PureParser.history(json, "org.example.app").single()
        assertEquals(23, entry.artifact.minSdk)
        assertEquals(34, entry.artifact.targetSdk)
        val signed = PureParser.history(json.replace("\"version_name\"", "\"sign\":[\"5EF5AE4028C98492E2B2ADE34FFF286605D5068F\",\"bad\"],\"version_name\""), "org.example.app").single()
        assertEquals(setOf("5ef5ae4028c98492e2b2ade34fff286605d5068f"), signed.artifact.signerDigests)
        assertTrue(entry.constraintsKnown)
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
            var progress = 0L to 0L
            assertEquals("fixture bytes", client.download(mirror, Source.ApkMirror, file) { bytes, total ->
                progress = bytes to total
            }.readText())
            assertEquals(13L to 13L, progress)
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

    @Test fun `download resumes a dropped transfer with Range and keeps the full hash`() {
        val directory = Files.createTempDirectory("web-resume-test").toFile()
        try {
            val ranges = mutableListOf<String?>()
            val client = WebSourceClient(OkHttpClient.Builder().addInterceptor { chain ->
                val range = chain.request().header("Range").also(ranges::add)
                if (range == null) {
                    // Announces 13 bytes, delivers 7, then the connection drops.
                    val dropping = object : okio.ForwardingSource(okio.Buffer().writeUtf8("fixture")) {
                        override fun read(sink: okio.Buffer, byteCount: Long): Long =
                            super.read(sink, byteCount).also { if (it < 0) throw java.io.IOException("connection abort") }
                    }
                    reply(chain.request(), 200).newBuilder().body(object : ResponseBody() {
                        override fun contentType() = null
                        override fun contentLength() = 13L
                        override fun source() = dropping.buffer()
                    }).build()
                } else reply(chain.request(), 206, " bytes").newBuilder().header("Content-Range", "bytes 7-12/13").build()
            }.build())
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest("fixture bytes".toByteArray())
                .joinToString("") { "%02x".format(it) }
            val file = client.download(mirror, Source.ApkMirror, File(directory, "download.apk"), expectedSha256 = sha)
            assertEquals("fixture bytes", file.readText())
            assertEquals(listOf(null, "bytes=7-"), ranges)
        } finally { directory.deleteRecursively() }
    }

    @Test fun `app search keeps app pages with their developer and the app page yields the package`() {
        // Structure of APKMirror's ?searchtype=app results and app pages (September 2026).
        val search = """
            <div class="appRow"><div class="table-row"><div class="table-cell">
              <h5 title="Firefox Fast &amp; Private Browser" class="appRowTitle"><a class="fontBlack" href="/apk/mozilla/firefox/">Firefox Fast &amp; Private Browser</a></h5>
              <a href="/apk/mozilla/" class="byDeveloper">by Mozilla</a></div></div></div>
            <div class="appRow"><div class="table-row"><div class="table-cell">
              <h5 class="appRowTitle"><a href="/apk/mozilla/firefox/firefox-150-release/">Firefox 150</a></h5></div></div></div>
        """.trimIndent()
        val apps = MirrorParser.searchApps(search, "https://www.apkmirror.com/?s=firefox")
        assertEquals(listOf(MirrorApp("Firefox Fast & Private Browser", "Mozilla", "https://www.apkmirror.com/apk/mozilla/firefox/")), apps)
        val page = """<a title="View on Play Store" href="https://play.google.com/store/apps/details?id=org.mozilla.firefox">Play</a>"""
        assertEquals("org.mozilla.firefox", MirrorParser.appPackage(page, apps.single().url))
        assertEquals(null, MirrorParser.appPackage("<p>Not on Play</p>", apps.single().url))
    }

    @Test fun `APKPure name search yields packages and unrelated padding is filtered out`() {
        // Shape of v3/search_query_new: matches first, then a bar of popular apps unrelated to the query.
        val json = """{"data":{"data":[
            {"data":[{"app_info":{"title":"Zoom Workplace","package_name":"us.zoom.videomeetings","developer":"zoom.com"}}]},
            {"data":[{"app_info":{"title":"Roblox","package_name":"com.roblox.client","developer":"Roblox Corporation"}},
                     {"app_info":{"title":"Toca Boca World","package_name":"com.tocaboca.tocalifeworld"}}]},
            {"data":[{"ad":true,"app_info":{"title":"Zoom Ad","package_name":"com.example.ad"}}]},
            {"data":[{"app_info":{"title":"Zoom Bad","package_name":"not a package"}}]}]}}"""
        val apps = PureParser.searchApps(json)
        assertEquals(listOf("us.zoom.videomeetings", "com.roblox.client"), apps.map { it.packageName })
        assertEquals(listOf("Zoom Workplace"), apps.filter { matchesQuery(it.name, "zoom workplace") }.map { it.name })
    }

    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/sources/$name")).readText()
    private fun reply(request: Request, code: Int, body: String = "") = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body.toResponseBody()).build()
}
