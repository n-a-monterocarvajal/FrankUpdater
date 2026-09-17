/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.ui

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.n_a_monterocarvajal.frankupdater.model.Source
import io.github.n_a_monterocarvajal.frankupdater.sources.sourceUrl

/** No JavaScript bridge or local-file access; Android's default SSL error handling cancels navigation. */
@Composable
internal fun AssistedWebDialog(url: String, source: Source, expected: String,
    onDismiss: () -> Unit, onBrowser: () -> Unit, onDownload: (String, Map<String, String>) -> Unit) {
    var message by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text("Selecciona $expected", style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                    TextButton(onClick = onBrowser) { Text("Usar navegador") }
                }
                if (message.isNotBlank()) Text(message)
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.setSupportMultipleWindows(false)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                !allowed(request.url.toString())
                            @Deprecated("Needed on API 23")
                            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = !allowed(url)
                            private fun allowed(value: String): Boolean = runCatching { sourceUrl(value, source, download = true) }.isSuccess
                        }
                        setDownloadListener { value, userAgent, _, _, _ ->
                            if (runCatching { sourceUrl(value, source, download = true) }.isFailure) {
                                message = "La descarga apunta fuera de la fuente. Puedes continuar en el navegador."
                            } else {
                                val headers = buildMap {
                                    put("User-Agent", userAgent)
                                    CookieManager.getInstance().getCookie(value)?.let { put("Cookie", it) }
                                }
                                onDownload(value, headers)
                            }
                        }
                        loadUrl(sourceUrl(url, source).toString())
                    }
                }, onRelease = { it.stopLoading(); it.destroy() })
            }
        }
    }
}
