/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.play

import com.aurora.gplayapi.data.models.PlayFile
import io.github.n_a_monterocarvajal.frankupdater.archive.sha256
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request

internal class DeliveryExpiredException : IOException("La dirección de descarga caducó. Vuelve a descargar para renovarla.")

/** Serial downloads; partial files are keyed by trusted content hash, never by URL or filename. */
internal class PlayDownload(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
) {
    fun download(files: List<PlayFile>, directory: File): File {
        PlayProvider.validateDelivery(files)
        require(files.sumOf { it.size } <= 3_221_225_472L) { "La descarga supera el límite de tamaño." }
        check(directory.isDirectory || directory.mkdirs())
        check(directory.usableSpace >= files.sumOf { it.size } * 2 + 67_108_864L) {
            "No hay espacio suficiente para preparar el paquete."
        }
        val payloads = files.map { remote ->
            val target = File(directory, "${remote.sha256.lowercase()}.apk")
            if (target.length() != remote.size || !target.sha256().equals(remote.sha256, true)) {
                val partial = File(directory, "${remote.sha256.lowercase()}.part")
                transfer(remote, partial)
                check(!target.exists() || target.delete())
                check(partial.renameTo(target)) { "No se pudo finalizar la descarga." }
            }
            remote to target
        }
        val archive = File(directory, "download.apks")
        try {
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                zip.setLevel(0)
                payloads.sortedBy { if (it.first.type == PlayFile.Type.BASE) 0 else 1 }
                    .forEachIndexed { index, (_, file) ->
                        zip.putNextEntry(ZipEntry(if (index == 0) "base.apk" else "split-$index.apk"))
                        file.inputStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                checkInterrupted()
                                val count = input.read(buffer)
                                if (count < 0) break
                                zip.write(buffer, 0, count)
                            }
                        }
                        zip.closeEntry()
                    }
            }
            return archive
        } catch (error: Exception) {
            archive.delete()
            throw error
        }
    }

    internal fun transfer(remote: PlayFile, partial: File) {
        if (partial.length() > remote.size) check(partial.delete())
        if (partial.length() == remote.size) {
            if (partial.sha256().equals(remote.sha256, true)) return
            check(partial.delete())
        }
        val offset = partial.length()
        check(partial.parentFile!!.usableSpace >= remote.size - offset + 67_108_864L) {
            "No hay espacio suficiente para descargar."
        }
        var url = PlayHttpClient.requireHttps(remote.url)
        repeat(6) { redirect ->
            checkInterrupted()
            val request = Request.Builder().url(url).header("Accept-Encoding", "identity").apply {
                if (offset > 0) header("Range", "bytes=$offset-")
            }.build()
            client.newCall(request).execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    require(redirect < 5)
                    url = PlayHttpClient.requireHttps(requireNotNull(url.resolve(requireNotNull(response.header("Location")))).toString())
                    return@repeat
                }
                if (response.code in setOf(401, 403, 410)) throw DeliveryExpiredException()
                if (response.code !in setOf(200, 206)) throw IOException("La descarga respondió HTTP ${response.code}.")
                val append = response.code == 206
                if (append) {
                    require(response.header("Content-Range") == "bytes $offset-${remote.size - 1}/${remote.size}") {
                        "El servidor devolvió un tramo de descarga incorrecto."
                    }
                }
                var written = if (append) offset else 0L
                try {
                    partial.outputStream(append).use { output ->
                        response.body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                checkInterrupted()
                                val count = input.read(buffer)
                                if (count < 0) break
                                written += count
                                require(written <= remote.size) { "La descarga supera el tamaño anunciado." }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (written != remote.size) throw IOException("La descarga quedó incompleta. Puedes reanudarla.")
                    require(partial.sha256().equals(remote.sha256, true)) { "El hash de la descarga no coincide." }
                } catch (error: IllegalArgumentException) {
                    partial.delete()
                    throw error
                }
                return
            }
        }
    }

    private fun File.outputStream(append: Boolean) = java.io.FileOutputStream(this, append).buffered()
    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Descarga cancelada.")
    }
}
