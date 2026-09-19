/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.play

import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.network.IHttpClient
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Bounded metadata transport. Credentials and signed URLs never enter logs. */
internal class PlayHttpClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
    /** Sent only with anonymous-server requests (postAuth, getAuth), for servers that require a given client identity. */
    private val authUserAgent: String? = null,
) : IHttpClient {
    init {
        require(authUserAgent == null || (authUserAgent.length in 1..256 && authUserAgent.all { it in ' '..'~' })) {
            "El User-Agent solo admite texto ASCII visible, hasta 256 caracteres."
        }
    }

    override val responseCode = MutableStateFlow(0)

    override fun get(url: String, headers: Map<String, String>): PlayResponse =
        execute(request(url, headers).get().build())

    override fun get(url: String, headers: Map<String, String>, params: Map<String, String>) =
        get(withParams(url, params), headers)

    override fun get(url: String, headers: Map<String, String>, paramString: String) =
        get(url + paramString, headers)

    override fun post(url: String, headers: Map<String, String>, body: ByteArray): PlayResponse =
        execute(request(url, headers).post(body.toRequestBody()).build())

    override fun post(url: String, headers: Map<String, String>, params: Map<String, String>) =
        post(withParams(url, params), headers, byteArrayOf())

    override fun getAuth(url: String) = get(url, authUserAgent?.let { mapOf("User-Agent" to it) }.orEmpty())

    override fun postAuth(url: String, body: ByteArray): PlayResponse = execute(
        request(url, authUserAgent?.let { mapOf("User-Agent" to it) }.orEmpty())
            .post(body.toRequestBody("application/json".toMediaType())).build(),
    )

    private fun request(url: String, headers: Map<String, String>) = Request.Builder()
        .url(requireHttps(url)).apply {
            headers.forEach { (name, value) -> header(name, value) }
            header("X-Limit-Ad-Tracking-Enabled", "true")
        }

    private fun withParams(url: String, params: Map<String, String>): String =
        requireHttps(url).newBuilder().apply {
            params.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build().toString()

    private fun execute(request: Request): PlayResponse = client.newCall(request).execute().use { response ->
        responseCode.value = response.code
        // Never expose the server's error body, which could contain credentials.
        if (!response.isSuccessful) throw IOException("El servicio respondió HTTP ${response.code}.")
        val bytes = ByteArrayOutputStream()
        response.body.byteStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(bytes.size() <= MAX_RESPONSE_BYTES - count) { "Respuesta del servicio demasiado grande." }
                bytes.write(buffer, 0, count)
            }
        }
        PlayResponse(isSuccessful = true, code = response.code, responseBytes = bytes.toByteArray())
    }

    companion object {
        const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024

        fun requireHttps(value: String): HttpUrl = value.toHttpUrl().also {
            require(it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.fragment == null) {
                "Usa una dirección HTTPS sin credenciales ni fragmentos."
            }
        }

        fun dispenserUrl(value: String): HttpUrl = requireHttps(value.trim()).also {
            require(it.query == null) { "La dirección del servidor no admite parámetros privados." }
        }
    }
}
