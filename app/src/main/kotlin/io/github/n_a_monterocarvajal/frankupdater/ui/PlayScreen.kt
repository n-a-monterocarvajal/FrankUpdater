/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import com.aurora.gplayapi.data.models.App
import io.github.n_a_monterocarvajal.frankupdater.compatibility.CatalogEntry
import io.github.n_a_monterocarvajal.frankupdater.compatibility.VersionCatalog
import io.github.n_a_monterocarvajal.frankupdater.device.AndroidGenericDeviceProfileProvider
import io.github.n_a_monterocarvajal.frankupdater.model.GenericDeviceProfile
import io.github.n_a_monterocarvajal.frankupdater.play.playCatalogEntry
import io.github.n_a_monterocarvajal.frankupdater.play.DeliveryExpiredException
import io.github.n_a_monterocarvajal.frankupdater.play.PlayDownload
import io.github.n_a_monterocarvajal.frankupdater.play.PlayProvider
import io.github.n_a_monterocarvajal.frankupdater.play.PlayCredentialStore
import io.github.n_a_monterocarvajal.frankupdater.play.PlayCredentials
import io.github.n_a_monterocarvajal.frankupdater.play.playDeviceProperties
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackageLibrary
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackagePipeline
import io.github.n_a_monterocarvajal.frankupdater.verification.catalogEntry
import io.github.n_a_monterocarvajal.frankupdater.compatibility.ReleaseChannel
import io.github.n_a_monterocarvajal.frankupdater.model.Source
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

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
    var query by rememberSaveable { mutableStateOf("") }
    var version by rememberSaveable { mutableStateOf("") }
    var provider by remember { mutableStateOf<PlayProvider?>(null) }
    var results by remember { mutableStateOf<List<App>>(emptyList()) }
    var selected by remember { mutableStateOf<App?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var catalogEntries by remember { mutableStateOf<List<CatalogEntry>>(emptyList()) }
    val device by produceState<GenericDeviceProfile?>(null) {
        value = runInterruptible(Dispatchers.IO) { AndroidGenericDeviceProfileProvider(context).getDeviceProfile() }
    }
    val scope = rememberCoroutineScope()

    fun act(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            message = ""
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (expired: DeliveryExpiredException) { message = expired.message.orEmpty() }
            catch (_: Exception) {
                message = "No se pudo completar la operación. Comprueba el servidor y la conexión. " +
                    "Si descargabas una versión, puede no estar disponible o no superar la verificación."
            } finally { busy = false }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("source-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            WebSourcesCard(pipeline, library, device, catalogEntries, requestedPackage, onPackageConsumed,
                onEntries = { incoming ->
                    val refreshed = incoming.map { it.artifact.packageName to it.artifact.source }.toSet()
                    catalogEntries = catalogEntries.filterNot {
                        (it.artifact.packageName to it.artifact.source) in refreshed
                    } + incoming.distinct()
                },
                onVerified = { catalogEntries = (catalogEntries + it).distinct() },
                playConnected = provider != null && !busy,
                onPlayVersion = { packageName, code ->
                    act {
                        selected = requireNotNull(provider).details(packageName)
                        version = code.toString()
                    }
                })
        }
        item { Text("Google Play", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp)) }
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
                        singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(aasToken, { aasToken = it }, label = { Text("AAS token") },
                        singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy && email.contains('@') && aasToken.isNotBlank(), onClick = {
                        act {
                            val properties = runInterruptible(Dispatchers.IO) { playDeviceProperties(context) }
                            val credentials = PlayCredentials(email.trim(), aasToken.trim())
                            provider = PlayProvider.personal(credentials, properties, Locale.getDefault())
                            credentialStore.save(credentials)
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
                        singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy && endpoint.isNotBlank(), onClick = {
                        act {
                            val properties = runInterruptible(Dispatchers.IO) { playDeviceProperties(context) }
                            provider = PlayProvider.anonymous(endpoint, properties, Locale.getDefault())
                            preferences.edit().putString("anonymous_server", endpoint.trim()).apply()
                            message = "Acceso anónimo conectado."
                        }
                    }) { Text("Conectar") }
                } else {
                    TextButton(enabled = !busy, onClick = { provider = null; results = emptyList(); selected = null }) {
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
                    enabled = !busy && provider != null, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy && provider != null && query.isNotBlank(), onClick = {
                    act {
                        results = requireNotNull(provider).search(query)
                        selected = null
                        if (results.isEmpty()) message = "No se encontraron aplicaciones."
                    }
                }) { Text("Buscar") }
            }
        }
        if (busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        if (message.isNotBlank()) item { UiStatus(message) }
        selected?.let { app ->
            item {
                UiSection(title = app.displayName, supporting = "Detalle y descarga") {
                        Text(app.packageName)
                        TextButton(enabled = !busy, onClick = { openPlayStore(context, app.packageName) }) {
                            Text("Abrir en Play Store")
                        }
                        Text("Disponibilidad", style = MaterialTheme.typography.titleMedium)
                        Text("Versión ofrecida por Play: ${app.versionName} (${app.versionCode})")
                        device?.let { profile ->
                            val selection = VersionCatalog().select(app.packageName,
                                catalogEntries.filter { it.artifact.packageName == app.packageName }, profile)
                            Text("Última versión conocida: ${selection.latestKnownVersionCode ?: "Sin datos"}")
                            Text("Última compatible según metadatos: ${selection.latestCompatibleVersionCode ?: "Pendiente de comprobar"}")
                            Text("El catálogo solo incluye versiones consultadas; puede haber publicaciones más recientes.")
                            selection.assessments.map { it.entry.artifact.versionCode }.distinct().forEach { code ->
                                TextButton(enabled = !busy, onClick = { version = code.toString() }) {
                                    Text("Solicitar código $code en Play")
                                }
                            }
                        }
                        Text("Descripción", style = MaterialTheme.typography.titleMedium)
                        Text(app.shortDescription.ifBlank { app.description }.take(800))
                        HorizontalDivider()
                        Text("Descarga verificada", style = MaterialTheme.typography.titleMedium)
                        Text("La identidad y la firma se comprobarán al descargar. Android confirmará si puede instalarse. " +
                            "Esta versión no representa necesariamente la última compatible.")
                        OutlinedTextField(version, { version = it.filter(Char::isDigit).take(19) },
                            label = { Text("Código de versión (opcional)") }, enabled = !busy,
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        val requested = if (version.isBlank()) app.versionCode else version.toLongOrNull()
                        Button(enabled = !busy && app.isFree && requested != null && requested > 0, onClick = {
                            act {
                                val files = requireNotNull(provider).delivery(app, requireNotNull(requested))
                                val directory = File(context.cacheDir, "play-download/${app.packageName}-$requested")
                                val archive = runInterruptible(Dispatchers.IO) { PlayDownload().download(files, directory) }
                                pipeline.importPlayDownload(archive, app.packageName, requested).use { prepared ->
                                    runInterruptible(Dispatchers.IO) { library.retain(prepared) }
                                    val verifiedEntry = runInterruptible(Dispatchers.IO) {
                                        prepared.verified.catalogEntry(Source.GooglePlay,
                                            "https://play.google.com/store/apps/details?id=${app.packageName}", ReleaseChannel.Unknown)
                                    }
                                    catalogEntries = (catalogEntries + verifiedEntry).distinct()
                                }
                                runInterruptible(Dispatchers.IO) { directory.deleteRecursively() }
                                message = "Paquete verificado y guardado. Abre Biblioteca para instalarlo."
                            }
                        }) { Text("Descargar a biblioteca") }
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
                            version = ""
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
