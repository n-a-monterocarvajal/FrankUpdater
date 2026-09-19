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
        // No total call timeout: large packages on slow links take longer than any fixed limit.
        // readTimeout still aborts a stalled transfer; metadata requests get 30 s per call below.
        .connectTimeout(25, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

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
        expectedSha256: String? = null, expectedSize: Long? = null,
        onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> }): File {
        require(expectedSha256 == null || Regex("[a-fA-F0-9]{64}").matches(expectedSha256))
        require(expectedSize == null || expectedSize > 0)
        require(!destination.exists())
        val parent = requireNotNull(destination.parentFile)
        check(parent.isDirectory || parent.mkdirs())
        val limit = minOf(1_610_612_736L, ((parent.usableSpace - 67_108_864L) / 2).coerceAtLeast(0))
        try {
            require(expectedSize == null || expectedSize <= limit)
            var bytes = 0L
            var total = -1L
            var retries = 0
            var bytesAtLastDrop = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            destination.outputStream().use { output ->
                while (true) {
                    try {
                        response(url, source, true, headers, resumeFrom = bytes).use { response ->
                            val remaining = response.body.contentLength()
                            if (bytes == 0L) {
                                require(remaining <= limit)
                                total = if (remaining > 0) remaining else expectedSize ?: -1
                            } else {
                                // Only accept the exact continuation; anything else would corrupt the hash.
                                if (response.code != 206 || response.header("Content-Range")?.startsWith("bytes $bytes-") != true) {
                                    throw NotResumable()
                                }
                                require(total < 0 || remaining < 0 || bytes + remaining == total)
                            }
                            response.body.byteStream().use { input ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    bytes += count
                                    require(bytes <= limit)
                                    require(expectedSize == null || bytes <= expectedSize)
                                    digest.update(buffer, 0, count)
                                    output.write(buffer, 0, count)
                                    onProgress(bytes, total)
                                }
                            }
                        }
                        break
                    } catch (error: IOException) {
                        // Resume after dropped connections (network switch, stalled read); never after cancellation.
                        // Retries reset whenever data arrived since the last drop; 5 s..25 s spans a ~75 s outage.
                        if (bytes > bytesAtLastDrop) retries = 0
                        bytesAtLastDrop = bytes
                        if (bytes == 0L || error is NotResumable || retries++ >= 5 || Thread.currentThread().isInterrupted) throw error
                        output.flush()
                        Thread.sleep(5_000L * retries)
                    }
                }
            }
            require(bytes > 0 && (total < 0 || bytes == total))
            require(expectedSize == null || bytes == expectedSize)
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            require(expectedSha256 == null || actualHash.equals(expectedSha256, ignoreCase = true))
            return destination
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    private fun response(url: String, source: Source, download: Boolean, headers: Map<String, String> = emptyMap(),
        resumeFrom: Long = 0): Response {
        var current = sourceUrl(url, source, download)
        repeat(6) { attempt ->
            val request = Request.Builder().url(current).header("User-Agent", userAgent(source)).apply {
                // Provider-specific metadata headers are never forwarded to a redirect destination.
                if (attempt == 0) headers.forEach { (name, value) -> header(name, value) }
                // Range is not provider metadata: the CDN at the end of the redirect chain must see it.
                if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-")
            }.build()
            val response = client.newCall(request).apply {
                if (!download) timeout().timeout(30, TimeUnit.SECONDS)
            }.execute()
            if (response.code in setOf(301, 302, 303, 307, 308)) {
                response.use {
                    val next = current.resolve(it.header("Location") ?: throw IOException("Redirección incompleta."))
                        ?: throw IOException("Redirección inválida.")
                    current = sourceUrl(next.toString(), source, download)
                }
            } else {
                if (response.code != 200 && !(resumeFrom > 0 && response.code == 206)) {
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

private class NotResumable : IOException("La fuente no permite reanudar la descarga.")

/**
 * APKMirror's Cloudflare rule challenges HTML (notably search) unless the User-Agent contains `APKUpdater`; the
 * RSS feed is exempt. Obtainium adopted the same token at af286fa; our own identity stays appended.
 */
internal fun userAgent(source: Source): String =
    if (source == Source.ApkMirror) "APKUpdater-v3.5.9 FrankUpdater/0.1" else "FrankUpdater/0.1"
