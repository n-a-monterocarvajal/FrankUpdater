/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.play

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/**
 * The connected Play session for the whole process: it survives tab switches and is shared with
 * PackageDownloadWorker. After the process dies, the worker reconnects with the last mode the user chose.
 */
internal object PlaySession {
    val provider = mutableStateOf<PlayProvider?>(null)

    suspend fun connectAnonymous(context: Context, endpoint: String, userAgent: String?): PlayProvider {
        val properties = runInterruptible(Dispatchers.IO) { playDeviceProperties(context) }
        return PlayProvider.anonymous(endpoint, properties, Locale.getDefault(), PlayHttpClient(authUserAgent = userAgent))
            .also {
                preferences(context).edit().putString("mode", "anonymous").putString("anonymous_server", endpoint)
                    .putString("anonymous_user_agent", userAgent.orEmpty()).apply()
                provider.value = it
            }
    }

    suspend fun connectPersonal(context: Context, credentials: PlayCredentials): PlayProvider {
        val properties = runInterruptible(Dispatchers.IO) { playDeviceProperties(context) }
        return PlayProvider.personal(credentials, properties, Locale.getDefault()).also {
            PlayCredentialStore(context).save(credentials)
            preferences(context).edit().putString("mode", "personal").apply()
            provider.value = it
        }
    }

    fun disconnect(context: Context) {
        provider.value = null
        preferences(context).edit().remove("mode").apply()
    }

    /** True when a download can use Play: connected now, or connected before and the worker can reconnect. */
    fun available(context: Context) = provider.value != null || preferences(context).getString("mode", null) != null

    /** The live session, or a new one from the saved choice; fails when the user never connected. */
    suspend fun current(context: Context): PlayProvider {
        provider.value?.let { return it }
        val saved = preferences(context)
        return when (saved.getString("mode", null)) {
            "personal" -> connectPersonal(context, requireNotNull(PlayCredentialStore(context).load()) {
                "Vuelve a conectar Google Play en Buscar."
            })
            "anonymous" -> connectAnonymous(context, requireNotNull(saved.getString("anonymous_server", null)),
                saved.getString("anonymous_user_agent", null)?.ifBlank { null })
            else -> throw IllegalStateException("Conecta Google Play en Buscar.")
        }
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences("play", 0)
}
