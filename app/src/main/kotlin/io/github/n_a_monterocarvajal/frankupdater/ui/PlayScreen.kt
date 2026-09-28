/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import com.aurora.gplayapi.data.models.App
import io.github.n_a_monterocarvajal.frankupdater.compatibility.CatalogEntry
import io.github.n_a_monterocarvajal.frankupdater.compatibility.VersionCatalog
import io.github.n_a_monterocarvajal.frankupdater.device.AndroidGenericDeviceProfileProvider
import io.github.n_a_monterocarvajal.frankupdater.model.GenericDeviceProfile
import io.github.n_a_monterocarvajal.frankupdater.model.PackageType
import io.github.n_a_monterocarvajal.frankupdater.play.playCatalogEntry
import io.github.n_a_monterocarvajal.frankupdater.play.DeliveryExpiredException
import io.github.n_a_monterocarvajal.frankupdater.play.PlayCredentialStore
import io.github.n_a_monterocarvajal.frankupdater.play.PlayCredentials
import io.github.n_a_monterocarvajal.frankupdater.play.PlaySession
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackageLibrary
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackagePipeline
import io.github.n_a_monterocarvajal.frankupdater.model.Source
import io.github.n_a_monterocarvajal.frankupdater.updates.DownloadRequest
import io.github.n_a_monterocarvajal.frankupdater.updates.PackageDownloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

// Survives tab switches together with the Search state in WebSourcesScreen.
private val searchCatalogEntries = mutableStateOf<List<CatalogEntry>>(emptyList())
// Scroll position too: coming back from another tab lands on the open version card and its download steps.
private val searchListState = LazyListState()

@Composable
internal fun PlayRoute(pipeline: LocalPackagePipeline, library: LocalPackageLibrary,
    requestedPackage: String? = null, onPackageConsumed: () -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val preferences = remember { context.getSharedPreferences("play", 0) }
    val credentialStore = remember { PlayCredentialStore(context) }
    var savedPersonal by remember { mutableStateOf(credentialStore.load()) }
    var email by remember { mutableStateOf(savedPersonal?.email.orEmpty()) }
    var aasToken by remember { mutableStateOf(savedPersonal?.aasToken.orEmpty()) }
    var endpoint by rememberSaveable { mutableStateOf(preferences.getString("anonymous_server", "").orEmpty()) }
    var serverUserAgent by rememberSaveable { mutableStateOf(preferences.getString("anonymous_user_agent", "").orEmpty()) }
    var query by rememberSaveable { mutableStateOf("") }
    // Process-wide session: it survives tab switches and the download worker uses it too.
    var provider by PlaySession.provider
    var results by remember { mutableStateOf<List<App>>(emptyList()) }
    var selected by remember { mutableStateOf<App?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Package handed from a Play result to the multi-source lookup at the top of the screen.
    var checkPackage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var catalogEntries by searchCatalogEntries
    val device by produceState<GenericDeviceProfile?>(null) {
        value = runInterruptible(Dispatchers.IO) { AndroidGenericDeviceProfileProvider(context).getDeviceProfile() }
    }
    val scope = rememberCoroutineScope()
    val listState = searchListState
    val keyboard = LocalSoftwareKeyboardController.current
    val focusSink = remember { FocusRequester() }

    fun act(action: suspend () -> Unit) {
        // Park focus off the text fields (see WebSourcesCard.act): clearFocus refocuses the first field on Android 8.
        keyboard?.hide(); runCatching { focusSink.requestFocus() }
        scope.launch {
            busy = true
            message = ""
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (expired: DeliveryExpiredException) { message = expired.message.orEmpty() }
            catch (error: Exception) {
                android.util.Log.w("FrankUpdater", "Play operation failed", error)
                // Only our own status messages and the lack of network are shown: other errors may carry signed URLs.
                val reason = when {
                    error is java.net.UnknownHostException -> " (sin conexión)"
                    error.message?.startsWith("El servicio respondió HTTP") == true -> " (${error.message?.removeSuffix(".")})"
                    else -> ""
                }
                message = "No se pudo completar la operación$reason. Comprueba el servidor y la conexión. " +
                    "Si descargabas una versión, puede no estar disponible o no superar la verificación."
            } finally { busy = false }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("source-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WebSourcesCard(pipeline, library, device, catalogEntries, requestedPackage ?: checkPackage,
                { onPackageConsumed(); checkPackage = null },
                onEntries = { incoming ->
                    val refreshed = incoming.map { it.artifact.packageName to it.artifact.source }.toSet()
                    catalogEntries = catalogEntries.filterNot {
                        (it.artifact.packageName to it.artifact.source) in refreshed
                    } + incoming.distinct()
                },
                onVerified = { catalogEntries = (catalogEntries + it).distinct() })
        }
        item {
            Text("Google Play", style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp).focusRequester(focusSink).focusable())
        }
        item {
            UiSection(
                title = if (provider == null) "Conectar Google Play" else "Google Play conectado",
                supporting = if (provider == null) "Elige una cuenta personal o acceso anónimo. Ambos son opcionales."
                    else "Ya puedes buscar, consultar detalles y solicitar una descarga.",
            ) {
                if (provider == null) {
                    Text("Cuenta personal", style = MaterialTheme.typography.titleMedium)
                    Text("Usa un correo y AAS token de un flujo compatible con Aurora. La contraseña no se guarda.")
                    OutlinedTextField(email, { email = it }, label = { Text("Correo de Google") },
                        singleLine = true, readOnly = busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(aasToken, { aasToken = it }, label = { Text("AAS token") },
                        singleLine = true, readOnly = busy, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy && email.contains('@') && aasToken.isNotBlank(), onClick = {
                        act {
                            val credentials = PlayCredentials(email.trim(), aasToken.trim())
                            PlaySession.connectPersonal(context, credentials)
                            savedPersonal = credentials
                            message = "Cuenta personal conectada."
                        }
                    }) { Text("Conectar cuenta personal") }
                    if (savedPersonal != null) TextButton(enabled = !busy, onClick = {
                        credentialStore.clear()
                        savedPersonal = null
                        email = ""
                        aasToken = ""
                    }) { Text("Borrar cuenta guardada") }
                    HorizontalDivider()
                    Text("Acceso anónimo", style = MaterialTheme.typography.titleMedium)
                    Text("Introduce un servidor compatible. Al conectar, se compartirá el perfil del dispositivo " +
                        "con ese servidor y Google para obtener aplicaciones adecuadas. No necesitas una cuenta personal.")
                    OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Dirección HTTPS del servidor") },
                        keyboardOptions = literalKeyboard(androidx.compose.ui.text.input.KeyboardType.Uri),
                        singleLine = true, readOnly = busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(serverUserAgent, { serverUserAgent = it.take(256) },
                        label = { Text("User-Agent (opcional)") },
                        supportingText = { Text("Solo si el servidor exige uno concreto.") },
                        keyboardOptions = literalKeyboard(androidx.compose.ui.text.input.KeyboardType.Ascii),
                        singleLine = true, readOnly = busy, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy && endpoint.isNotBlank(), onClick = {
                        act {
                            PlaySession.connectAnonymous(context, endpoint.trim(), serverUserAgent.trim().ifEmpty { null })
                            message = "Acceso anónimo conectado."
                        }
                    }) { Text("Conectar") }
                    // Result of connecting shown next to the buttons, not at the end of the screen (F-52).
                    if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (message.isNotBlank()) UiStatus(message)
                } else {
                    TextButton(enabled = !busy, onClick = { PlaySession.disconnect(context); results = emptyList(); selected = null }) {
                        Text("Desconectar")
                    }
                }
            }
        }
        item {
            UiSection(
                title = "Buscar en Google Play",
                supporting = if (provider == null) "Conecta una cuenta o un servidor anónimo para comenzar."
                    else "Los resultados se verifican antes de guardar cualquier descarga.",
            ) {
                OutlinedTextField(query, { query = it.take(200) }, label = { Text("Buscar aplicaciones") },
                    enabled = provider != null, readOnly = busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy && provider != null && query.isNotBlank(), onClick = {
                    act {
                        results = requireNotNull(provider).search(query)
                        selected = null
                        if (results.isEmpty()) message = "No se encontraron aplicaciones."
                    }
                }) { Text("Buscar") }
            }
        }
        if (provider != null && busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        if (provider != null && message.isNotBlank()) item { UiStatus(message) }
        selected?.let { app ->
            item {
                UiSection(title = app.displayName, supporting = "Detalle y descarga") {
                        Text(app.packageName)
                        TextButton(enabled = !busy, onClick = { openPlayStore(context, app.packageName) }) {
                            Text("Abrir en Play Store")
                        }
                        Text("Disponibilidad", style = MaterialTheme.typography.titleMedium)
                        device?.let { profile ->
                            val selection = VersionCatalog().select(app.packageName,
                                catalogEntries.filter { it.artifact.packageName == app.packageName }, profile)
                            Text("Última versión conocida: ${selection.latestKnownVersionCode ?: "Sin datos"}")
                            Text("Última compatible según metadatos: ${selection.latestCompatibleVersionCode ?: "Pendiente de comprobar"}")
                            Text("El catálogo solo incluye versiones consultadas; puede haber publicaciones más recientes.")
                            // Play alone cannot say which version this device supports (F-60): hand the package to the
                            // multi-source lookup at the top, where every version card offers Play as one of its routes.
                            Button(enabled = !busy, onClick = {
                                checkPackage = app.packageName
                                scope.launch { listState.animateScrollToItem(0) }
                            }) {
                                Text(if (selection.latestCompatibleVersionCode == null) "Comprobar compatibilidad en todas las fuentes"
                                    else "Ver versiones compatibles y descargar")
                            }
                        }
                        Text("Descripción", style = MaterialTheme.typography.titleMedium)
                        Text(app.shortDescription.ifBlank { app.description }.take(800))
                        HorizontalDivider()
                        Text("Versión ofrecida por Play", style = MaterialTheme.typography.titleMedium)
                        Text("${app.versionName} (código ${app.versionCode}). Play la elige para el perfil de dispositivo " +
                            "de la sesión; puede no ser la última compatible. La identidad y la firma se comprueban al descargar.")
                        fun offered(install: Boolean) = PackageDownloads.enqueue(context, DownloadRequest(app.packageName,
                            app.versionCode.toLong(), Source.GooglePlay, PackageType.SplitApkSet,
                            "https://play.google.com/store/apps/details?id=${app.packageName}", install = install))
                        OutlinedButton(enabled = !busy && app.isFree && app.versionCode > 0, onClick = { offered(true) }) {
                            Text("Descargar e instalar ${app.versionName}")
                        }
                        DownloadSteps(app.packageName, app.versionCode.toLong(), onRetry = { _, install -> offered(install) })
                        if (!app.isFree) Text("El acceso anónimo no admite compras.")
                }
            }
        }
        items(results, key = { it.packageName }) { app ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(app.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(app.packageName)
                    TextButton(enabled = !busy, onClick = {
                        act {
                            selected = requireNotNull(provider).details(app.packageName)
                            val details = requireNotNull(selected)
                            if (details.versionCode > 0) catalogEntries = (catalogEntries +
                                playCatalogEntry(details.packageName, details.versionCode, details.versionName)).distinct()
                        }
                    }) { Text("Ver detalles") }
                }
            }
        }
    }
}

private fun openPlayStore(context: android.content.Context, packageName: String) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        .setPackage("com.android.vending")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = Intent(Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(market) }.getOrElse { context.startActivity(fallback) }
}
