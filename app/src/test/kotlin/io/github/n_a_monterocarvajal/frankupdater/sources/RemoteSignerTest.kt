package io.github.n_a_monterocarvajal.frankupdater.sources

import com.android.apksig.ApkVerifier
import io.github.n_a_monterocarvajal.frankupdater.model.Source
import java.io.File
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteSignerTest {
    private val url = "https://f-droid.org/repo/org.example.app_1.apk"

    @Test fun `range reads find the same current signer as full verification`() {
        for (name in listOf("golden-aligned-v1v2v3-out.apk", "golden-aligned-v1v2v3-lineage-out.apk")) {
            val apk = fixture(name)
            val requests = mutableListOf<String>()
            val signer = remoteSignerSha256(serving(apk.readBytes(), requests), url, Source.FDroid)
            val expected = ApkVerifier.Builder(apk).build().verify().signerCertificates.first().encoded.sha256()
            assertEquals(name, expected, signer)
            assertEquals(name, true, requests.size <= 3)
        }
    }

    @Test fun `v1-only APKs and servers without ranges give no hint`() {
        val v1 = fixture("v1-only-two-signers.apk").readBytes()
        assertNull(remoteSignerSha256(serving(v1, mutableListOf()), url, Source.FDroid))
        val noRanges = WebSourceClient(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("ok")
                .body(v1.toResponseBody()).build()
        }.build())
        assertNull(remoteSignerSha256(noRanges, url, Source.FDroid))
    }

    /** Minimal HTTP range server over [bytes]; records each Range header. */
    private fun serving(bytes: ByteArray, requests: MutableList<String>) = WebSourceClient(OkHttpClient.Builder()
        .addInterceptor { chain ->
            val range = requireNotNull(chain.request().header("Range")).also(requests::add).removePrefix("bytes=")
            val (from, to) = if (range.startsWith("-")) {
                maxOf(0, bytes.size - range.drop(1).toInt()) to bytes.size - 1
            } else range.split('-').let { it[0].toInt() to minOf(it[1].toInt(), bytes.size - 1) }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(206).message("partial")
                .header("Content-Range", "bytes $from-$to/${bytes.size}")
                .body(bytes.copyOfRange(from, to + 1).toResponseBody()).build()
        }.build())

    private fun fixture(name: String) = File(requireNotNull(javaClass.getResource("/signatures/$name")).toURI())
    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
}
