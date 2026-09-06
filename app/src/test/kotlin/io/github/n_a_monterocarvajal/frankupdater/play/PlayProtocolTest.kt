package io.github.n_a_monterocarvajal.frankupdater.play

import com.aurora.gplayapi.data.models.PlayFile
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class PlayProtocolTest {
    @Test fun `anonymous response requires bounded string credentials`() {
        assertEquals("anonymous@example.invalid" to "test-token", PlayProvider.parseAnonymousResponse(
            """{"email":"anonymous@example.invalid","auth":"test-token"}""".toByteArray()))
        for (json in listOf("{}", """{"email":"x","auth":""}""", """{"email":"x","auth":42}""")) {
            assertThrows(Exception::class.java) { PlayProvider.parseAnonymousResponse(json.toByteArray()) }
        }
        assertThrows(IllegalArgumentException::class.java) { PlayProvider.parseAnonymousResponse(ByteArray(65_537)) }
    }

    @Test fun `server URL requires HTTPS and excludes credentials and query secrets`() {
        assertEquals("https://example.invalid/api/auth", PlayHttpClient.dispenserUrl("https://example.invalid/api/auth").toString())
        for (url in listOf("http://example.invalid", "https://user:secret@example.invalid", "https://example.invalid?token=secret", "https://example.invalid/#secret")) {
            assertThrows(IllegalArgumentException::class.java) { PlayHttpClient.dispenserUrl(url) }
        }
    }

    @Test fun `metadata transport refuses redirects without sending a second request`() {
        val requests = AtomicInteger()
        val http = PlayHttpClient(OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("redirect")
                .header("Location", "https://other.invalid").body("private-token".toResponseBody()).build()
        }.build())
        val error = assertThrows(IOException::class.java) { http.get("https://example.invalid", mapOf("Authorization" to "Bearer secret")) }
        assertEquals(1, requests.get())
        assertFalse(error.message.orEmpty().contains("secret"))
        assertFalse(error.message.orEmpty().contains("private-token"))
    }

    @Test fun `delivery rejects missing hashes alternate bases and unsupported payloads`() {
        val base = PlayFile(name = "base.apk", url = "https://example.invalid/base", size = 12, sha256 = "a".repeat(64))
        PlayProvider.validateDelivery(listOf(base, base.copy(name = "split.apk", type = PlayFile.Type.SPLIT)))
        for (files in listOf(emptyList(), listOf(base, base), listOf(base.copy(sha256 = "")), listOf(base, base.copy(name = "data.obb", type = PlayFile.Type.OBB)))) {
            assertThrows(IllegalArgumentException::class.java) { PlayProvider.validateDelivery(files) }
        }
    }
}
