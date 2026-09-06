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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.gplayapi.data.models.App
import io.github.n_a_monterocarvajal.frankupdater.play.DeliveryExpiredException
import io.github.n_a_monterocarvajal.frankupdater.play.PlayDownload
import io.github.n_a_monterocarvajal.frankupdater.play.PlayProvider
import io.github.n_a_monterocarvajal.frankupdater.play.playDeviceProperties
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackageLibrary
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackagePipeline
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

@Composable
internal fun PlayRoute(pipeline: LocalPackagePipeline, library: LocalPackageLibrary) {
    val context = LocalContext.current.applicationContext
    val preferences = remember { context.getSharedPreferences("play", 0) }
    var endpoint by rememberSaveable { mutableStateOf(preferences.getString("anonymous_server", "").orEmpty()) }
    var query by rememberSaveable { mutableStateOf("") }
    var version by rememberSaveable { mutableStateOf("") }
    var provider by remember { mutableStateOf<PlayProvider?>(null) }
    var results by remember { mutableStateOf<List<App>>(emptyList()) }
    var selected by remember { mutableStateOf<App?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Google Play", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp)) }
        item {
            if (provider == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Acceso anónimo")
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
                }
            } else {
                TextButton(enabled = !busy, onClick = { provider = null; results = emptyList(); selected = null }) {
                    Text("Desconectar acceso anónimo")
                }
            }
        }
        item {
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
        if (busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        if (message.isNotBlank()) item { Text(message) }
        selected?.let { app ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(app.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(app.packageName)
                        Text("Versión ofrecida por Play: ${app.versionName} (${app.versionCode})")
                        Text(app.shortDescription.ifBlank { app.description }.take(800))
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
                                }
                                runInterruptible(Dispatchers.IO) { directory.deleteRecursively() }
                                message = "Paquete verificado y guardado. Abre Biblioteca para instalarlo."
                            }
                        }) { Text("Descargar a biblioteca") }
                        if (!app.isFree) Text("El acceso anónimo no admite compras.")
                    }
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
                            version = ""
                        }
                    }) { Text("Ver detalles") }
                }
            }
        }
    }
}
