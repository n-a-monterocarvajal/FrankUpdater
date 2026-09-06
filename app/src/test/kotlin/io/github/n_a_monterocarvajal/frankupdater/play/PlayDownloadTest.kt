package io.github.n_a_monterocarvajal.frankupdater.play

import com.aurora.gplayapi.data.models.PlayFile
import java.nio.file.Files
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class PlayDownloadTest {
    private val bytes = "verified APK payload".toByteArray()
    private val remote = PlayFile(name = "base.apk", url = "https://example.invalid/base", size = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })

    @Test fun `206 resumes the exact advertised range and validates the complete hash`() {
        withPartial { partial ->
            partial.writeBytes(bytes.take(5).toByteArray())
            val download = downloader { request ->
                assertEquals("bytes=5-", request.header("Range"))
                response(request, 206, bytes.drop(5).toByteArray(), "bytes 5-${bytes.size - 1}/${bytes.size}")
            }
            download.transfer(remote, partial)
            assertArrayEquals(bytes, partial.readBytes())
        }
    }

    @Test fun `200 after Range restarts instead of appending`() {
        withPartial { partial ->
            partial.writeText("old partial")
            downloader { response(it, 200, bytes) }.transfer(remote, partial)
            assertArrayEquals(bytes, partial.readBytes())
        }
    }

    @Test fun `wrong range is rejected and corrupted completed download is deleted`() {
        withPartial { partial ->
            partial.writeBytes(bytes.take(5).toByteArray())
            assertThrows(IllegalArgumentException::class.java) {
                downloader { response(it, 206, bytes, "bytes 0-${bytes.size - 1}/${bytes.size}") }.transfer(remote, partial)
            }
            assertEquals(5, partial.length())
            assertThrows(IllegalArgumentException::class.java) {
                downloader { response(it, 200, ByteArray(bytes.size)) }.transfer(remote, partial)
            }
            assertFalse(partial.exists())
        }
    }

    @Test fun `expired URL preserves partial bytes for the next delivery request`() {
        withPartial { partial ->
            partial.writeBytes(bytes.take(5).toByteArray())
            assertThrows(DeliveryExpiredException::class.java) {
                downloader { response(it, 403, byteArrayOf()) }.transfer(remote, partial)
            }
            assertEquals(5, partial.length())
        }
    }

    @Test fun `redirect cannot downgrade HTTPS`() {
        withPartial { partial ->
            assertThrows(IllegalArgumentException::class.java) {
                downloader { response(it, 302, byteArrayOf()).newBuilder().header("Location", "http://example.invalid/base").build() }
                    .transfer(remote, partial)
            }
        }
    }

    private fun downloader(reply: (Request) -> Response) = PlayDownload(OkHttpClient.Builder()
        .followRedirects(false).addInterceptor { reply(it.request()) }.build())

    private fun response(request: Request, code: Int, data: ByteArray, range: String? = null) =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .body(data.toResponseBody()).apply { if (range != null) header("Content-Range", range) }.build()

    private fun withPartial(test: (java.io.File) -> Unit) {
        val partial = Files.createTempFile("play-resume", ".part").toFile()
        try { test(partial) } finally { partial.delete() }
    }
}
