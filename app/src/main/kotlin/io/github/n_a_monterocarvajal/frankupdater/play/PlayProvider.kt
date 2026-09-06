/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Anonymous login adapted from Aurora Store AuthProvider at
 * 660670a35cd6980afaf1f9667b7df2144ddc435c. Protocol delegates to GPlayApi 3.6.4.
 */
package io.github.n_a_monterocarvajal.frankupdater.play

import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.aurora.gplayapi.helpers.SearchHelper
import com.aurora.gplayapi.network.IHttpClient
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.util.Locale
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

internal class PlayProvider private constructor(
    private val auth: AuthData,
    private val http: IHttpClient,
) {
    suspend fun search(query: String): List<App> = runInterruptible(Dispatchers.IO) {
        require(query.isNotBlank() && query.length <= 200)
        val bundle = SearchHelper(auth).using(http).searchResults(query.trim(), "")
        bundle.streamClusters.values.flatMap { it.clusterAppList }.distinctBy { it.packageName }
    }

    suspend fun details(packageName: String): App = runInterruptible(Dispatchers.IO) {
        requirePackageName(packageName)
        AppDetailsHelper(auth).using(http).getAppByPackageName(packageName).also {
            require(it.packageName == packageName) { "Google Play devolvió otra aplicación." }
        }
    }

    /** Explicit user-requested delivery; never called by search or app details. */
    suspend fun delivery(app: App, versionCode: Long): List<PlayFile> = runInterruptible(Dispatchers.IO) {
        requirePackageName(app.packageName)
        require(versionCode > 0)
        require(app.isFree) { "El acceso anónimo solo admite aplicaciones gratuitas." }
        PurchaseHelper(auth).using(http)
            .purchase(app.packageName, versionCode, app.offerType)
            .also(::validateDelivery)
    }

    companion object {
        suspend fun anonymous(
            endpoint: String,
            properties: Properties,
            locale: Locale,
            http: IHttpClient = PlayHttpClient(),
        ): PlayProvider = runInterruptible(Dispatchers.IO) {
            val url = PlayHttpClient.dispenserUrl(endpoint).toString()
            val response = http.postAuth(url, Gson().toJson(properties).toByteArray(Charsets.UTF_8))
            require(response.isSuccessful) { "El servidor anónimo no pudo iniciar la sesión." }
            val (email, token) = parseAnonymousResponse(response.responseBytes)
            // AuthHelper owns mutable global transport configuration; serialize its use.
            val auth = synchronized(AuthHelper) {
                AuthHelper.using(http).build(email, token, AuthHelper.Token.AUTH, true, properties, locale)
            }
            PlayProvider(auth, http)
        }

        fun parseAnonymousResponse(bytes: ByteArray): Pair<String, String> {
            require(bytes.size <= 64 * 1024) { "Respuesta de acceso anónimo demasiado grande." }
            val json = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            fun field(name: String): String {
                val value = requireNotNull(json[name]) { "Respuesta de acceso anónimo incompleta." }
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                return value.asString.also { text ->
                    require(text.isNotBlank() && text.none(Char::isISOControl)) { "Respuesta de acceso anónimo inválida." }
                }
            }
            return field("email") to field("auth")
        }

        fun validateDelivery(files: List<PlayFile>) {
            require(files.count { it.type == PlayFile.Type.BASE } == 1) { "La descarga necesita exactamente un APK base." }
            require(files.size <= 512 && files.map { it.name }.distinct().size == files.size)
            files.forEach {
                require(it.type in setOf(PlayFile.Type.BASE, PlayFile.Type.SPLIT)) {
                    "Este paquete necesita archivos adicionales que aún no admite la descarga de Play."
                }
                PlayHttpClient.requireHttps(it.url)
                require(it.size in 1..1_610_612_736L)
                require(it.sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Falta el hash SHA-256 del archivo." }
            }
        }

        private fun requirePackageName(value: String) {
            require(value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")))
        }
    }
}
