/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.sources

import io.github.n_a_monterocarvajal.frankupdater.model.Source
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class WebSourceClient(client: OkHttpClient = OkHttpClient()) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(25, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES).build()

    fun text(url: String, source: Source, headers: Map<String, String> = emptyMap()): String =
        response(url, source, false, headers).use { response ->
            val output = ByteArrayOutputStream()
            response.body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() <= 8 * 1024 * 1024 - count)
                    output.write(buffer, 0, count)
                }
            }
            output.toString("UTF-8")
        }

    fun download(url: String, source: Source, destination: File, headers: Map<String, String> = emptyMap(),
        expectedSha256: String? = null, expectedSize: Long? = null): File {
        require(expectedSha256 == null || Regex("[a-fA-F0-9]{64}").matches(expectedSha256))
        require(expectedSize == null || expectedSize > 0)
        require(!destination.exists())
        val parent = requireNotNull(destination.parentFile)
        check(parent.isDirectory || parent.mkdirs())
        val limit = minOf(1_610_612_736L, ((parent.usableSpace - 67_108_864L) / 2).coerceAtLeast(0))
        try {
            require(expectedSize == null || expectedSize <= limit)
            response(url, source, true, headers).use { response ->
                val expected = response.body.contentLength()
                require(expected <= limit)
                var bytes = 0L
                val digest = MessageDigest.getInstance("SHA-256")
                response.body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            require(bytes <= limit)
                            require(expectedSize == null || bytes <= expectedSize)
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                require(bytes > 0 && (expected < 0 || bytes == expected))
                require(expectedSize == null || bytes == expectedSize)
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                require(expectedSha256 == null || actualHash.equals(expectedSha256, ignoreCase = true))
            }
            return destination
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    private fun response(url: String, source: Source, download: Boolean, headers: Map<String, String> = emptyMap()): Response {
        var current = sourceUrl(url, source, download)
        repeat(6) { attempt ->
            val request = Request.Builder().url(current).header("User-Agent", "FrankUpdater/0.1").apply {
                // Provider-specific metadata headers are never forwarded to a redirect destination.
                if (attempt == 0) headers.forEach { (name, value) -> header(name, value) }
            }.build()
            val response = client.newCall(request).execute()
            if (response.code in setOf(301, 302, 303, 307, 308)) {
                response.use {
                    val next = current.resolve(it.header("Location") ?: throw IOException("Redirección incompleta."))
                        ?: throw IOException("Redirección inválida.")
                    current = sourceUrl(next.toString(), source, download)
                }
            } else {
                if (response.code != 200) {
                    val code = response.code
                    response.close()
                    throw IOException("La fuente respondió HTTP $code.")
                }
                return response
            }
        }
        throw IOException("Demasiadas redirecciones.")
    }
}
