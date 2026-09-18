/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.n_a_monterocarvajal.frankupdater.compatibility.*
import io.github.n_a_monterocarvajal.frankupdater.model.*
import io.github.n_a_monterocarvajal.frankupdater.sources.*
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackageLibrary
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackagePipeline
import io.github.n_a_monterocarvajal.frankupdater.verification.VerificationExpectations
import io.github.n_a_monterocarvajal.frankupdater.verification.catalogEntry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*

private data class WebChoice(val packageName: String, val versionCode: Long?, val source: Source,
    val type: PackageType, val url: String, val directUrl: String? = null,
    val sha256: String? = null, val sizeBytes: Long? = null,
    val channel: ReleaseChannel = ReleaseChannel.Unknown)

@Composable
internal fun WebSourcesCard(
    pipeline: LocalPackagePipeline, library: LocalPackageLibrary, device: GenericDeviceProfile?,
    entries: List<CatalogEntry>, onEntries: (List<CatalogEntry>) -> Unit,
    onVerified: (CatalogEntry) -> Unit,
    playConnected: Boolean, onPlayVersion: (String, Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { WebSourceClient() }
    var packageName by rememberSaveable { mutableStateOf("") }
    var mirrorUrl by rememberSaveable { mutableStateOf("") }
    var releases by remember { mutableStateOf<List<WebRelease>>(emptyList()) }
    var variants by remember { mutableStateOf<List<MirrorVariant>>(emptyList()) }
    var choice by remember { mutableStateOf<WebChoice?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var includePreviews by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var failedSources by remember { mutableStateOf<Set<Source>>(emptySet()) }
    var pendingImport by remember { mutableStateOf<WebChoice?>(null) }
    var assisted by remember { mutableStateOf<WebChoice?>(null) }
    var shown by remember(packageName, releases, variants, entries) { mutableIntStateOf(50) }
    LaunchedEffect(variants, packageName) {
        if (variants.isNotEmpty()) onEntries(variants.mapNotNull { it.catalogEntry(packageName) })
    }

    suspend fun download(selected: WebChoice, expectedCode: Long, capturedUrl: String? = null, headers: Map<String, String> = emptyMap()) {
        val directory = File(context.cacheDir, "web-download/${UUID.randomUUID()}")
        val extension = when (selected.type) { PackageType.Apkm -> "apkm"; PackageType.Xapk -> "xapk"; else -> "apk" }
        try {
            val archive = runInterruptible(Dispatchers.IO) {
                val url = capturedUrl ?: selected.directUrl ?: run {
                    val page = MirrorParser.downloadPage(client.text(selected.url, selected.source), selected.url)
                    MirrorParser.downloadUrl(client.text(page, selected.source), page)
                }
                client.download(url, selected.source, File(directory, "download.$extension"), headers, selected.sha256, selected.sizeBytes)
            }
            pipeline.importDownloadedArchive(archive, "${selected.packageName}-$expectedCode.$extension",
                selected.packageName, expectedCode, selected.url, if (capturedUrl == null) "direct-${selected.source.name}" else "assisted-web").use {
                prepared ->
                val verifiedEntry = runInterruptible(Dispatchers.IO) {
                    library.retain(prepared)
                    prepared.verified.catalogEntry(selected.source, selected.url, selected.channel)
                }
                onVerified(verifiedEntry)
            }
            message = "Paquete verificado y guardado en Biblioteca."
        } finally { withContext(Dispatchers.IO + NonCancellable) { directory.deleteRecursively() } }
    }

    fun act(source: Source, action: suspend () -> Unit) {
        scope.launch {
            busy = true
            message = ""
            try { action(); failedSources = failedSources - source }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                failedSources = failedSources + source
                message = "No se pudo completar la operación. Puedes continuar en la web e importar el archivo; " +
                    "su paquete, versión y firma se comprobarán antes de conservarlo."
            } finally { busy = false }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val expected = pendingImport
        pendingImport = null
        if (uri != null && expected != null) act(expected.source) {
            pipeline.import(uri, VerificationExpectations(expected.packageName, requireNotNull(expected.versionCode))).use {
                prepared ->
                require(expected.sha256 == null || expected.sha256.equals(prepared.verified.sourceSha256, ignoreCase = true))
                require(expected.sizeBytes == null || expected.sizeBytes == prepared.verified.sourceSizeBytes)
                runInterruptible(Dispatchers.IO) { library.retain(prepared) }
                onVerified(runInterruptible(Dispatchers.IO) {
                    prepared.verified.catalogEntry(expected.source, expected.url, expected.channel)
                })
            }
            message = "Paquete verificado y guardado en Biblioteca."
        }
    }

    fun select(value: WebChoice) { choice = value; code = value.versionCode?.toString().orEmpty() }
    fun browser(url: String, source: Source) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl(url, source).toString())))
        } catch (_: Exception) { message = "No se pudo abrir el navegador." }
    }

    assisted?.let { selected ->
        AssistedWebDialog(selected.url, selected.source, "${selected.packageName} (${selected.versionCode}) · ${selected.type}",
            onDismiss = { assisted = null },
            onBrowser = { assisted = null; browser(selected.url, selected.source) },
            onDownload = { url, headers ->
                assisted = null
                act(selected.source) { download(selected, requireNotNull(selected.versionCode), url, headers) }
            })
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Historial y otras fuentes", style = MaterialTheme.typography.titleLarge)
            Text(
                "Consulta APKMirror o APKPure sin iniciar sesión en Play. La consulta comparte el paquete con la fuente elegida.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Consultar fuentes", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(packageName, {
                packageName = it.take(255); releases = emptyList(); variants = emptyList(); choice = null; failedSources = emptySet()
            }, label = { Text("Paquete (ejemplo: org.fossify.math)") }, singleLine = true, enabled = !busy,
                modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && device != null && packageName.isNotBlank(), onClick = {
                act(Source.ApkPure) {
                    requirePackageName(packageName)
                    val found = runInterruptible(Dispatchers.IO) {
                        PureParser.history(client.text("https://tapi.pureapk.com/v3/get_app_his_version?package_name=$packageName&hl=en",
                            Source.ApkPure, mapOf("Ual-Access-Businessid" to "projecta", "Ual-Access-ProjectA" to
                                "{\"device_info\":{\"os_ver\":\"${requireNotNull(device).sdk}\"}}")), packageName)
                    }
                    onEntries(found)
                    message = "Historial consultado. Revisa versión y variante antes de descargar."
                }
            }) { Text("Consultar APKPure") }
            OutlinedTextField(mirrorUrl, { mirrorUrl = it.take(2048); releases = emptyList(); variants = emptyList(); choice = null },
                label = { Text("Página de aplicación o release en APKMirror") }, singleLine = true, enabled = !busy,
                modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && packageName.isNotBlank() && mirrorUrl.isNotBlank(), onClick = {
                act(Source.ApkMirror) {
                    requirePackageName(packageName)
                    runInterruptible(Dispatchers.IO) {
                        val html = client.text(mirrorUrl, Source.ApkMirror)
                        if (sourceUrl(mirrorUrl, Source.ApkMirror).encodedPath.contains("-release/")) {
                            variants = MirrorParser.variants(html, mirrorUrl); releases = emptyList()
                        } else { releases = MirrorParser.releases(html, mirrorUrl); variants = emptyList() }
                    }
                }
            }) { Text("Consultar APKMirror") }
            if (mirrorUrl.isNotBlank()) TextButton(enabled = !busy, onClick = {
                browser(mirrorUrl, Source.ApkMirror)
            }) { Text("Abrir APKMirror en navegador") }
            TextButton(enabled = !busy && packageName.isNotBlank(), onClick = {
                try { requirePackageName(packageName); browser("https://apkpure.com/apk/$packageName/versions", Source.ApkPure) }
                catch (_: Exception) { message = "Introduce un nombre de paquete válido." }
            }) { Text("Abrir historial APKPure en navegador") }
            if (releases.isNotEmpty() || variants.isNotEmpty() || entries.any { it.artifact.packageName == packageName }) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Resultados y compatibilidad", style = MaterialTheme.typography.titleMedium)
            }
            releases.take(shown).forEach { release ->
                TextButton(enabled = !busy, onClick = {
                    act(Source.ApkMirror) {
                        variants = runInterruptible(Dispatchers.IO) { MirrorParser.variants(client.text(release.url, Source.ApkMirror), release.url) }
                        releases = emptyList()
                    }
                }) { Text(release.name) }
            }
            variants.take(shown).forEach { variant ->
                TextButton(enabled = !busy, onClick = {
                    select(WebChoice(packageName, variant.versionCode, Source.ApkMirror, variant.type, variant.url, channel = variant.channel))
                }) { Text("${variant.name} · ${variant.architecture} · ${variant.minimumAndroid} · ${variant.density} · ${variant.type}") }
            }
            device?.let { profile ->
                Row {
                    Checkbox(includePreviews, onCheckedChange = { includePreviews = it })
                    Text("Incluir versiones preliminares (alpha/beta/RC)")
                }
                val selection = VersionCatalog().select(packageName.ifBlank { "unknown.app" },
                    entries.filter { it.artifact.packageName == packageName }, profile, unavailableSources = failedSources,
                    includePreviews = includePreviews)
                if (selection.assessments.isNotEmpty()) {
                    Text("Última versión conocida: ${selection.latestKnownVersionCode}", style = MaterialTheme.typography.titleSmall)
                    Text("Última compatible: ${selection.latestCompatibleVersionCode ?: "Pendiente de comprobar"}")
                    Text(
                        "El historial puede estar incompleto. Los metadatos no sustituyen la verificación del archivo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (selection.hasUnresolvedNewerVersion) {
                        Text("Hay versiones superiores pendientes de comprobar.", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                selection.assessments.take(shown).forEach { assessment ->
                    val artifact = assessment.entry.artifact
                    Text("${artifact.versionName.orEmpty()} (${artifact.versionCode}) · ${artifact.source} · " +
                        "${artifact.abis.ifEmpty { listOf("ABI no especificada") }.joinToString()} · ${artifact.packageType}",
                        style = MaterialTheme.typography.titleSmall)
                    Text(when (assessment.entry.channel) {
                        ReleaseChannel.Preview -> "Canal preliminar"
                        ReleaseChannel.Stable -> "Canal estable"
                        ReleaseChannel.Unknown -> "Canal no confirmado"
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (assessment.incompatibilities.isNotEmpty()) {
                        Text("No compatible: ${assessment.incompatibilities.joinToString()}", color = MaterialTheme.colorScheme.error)
                    }
                    else if (assessment.channelAllowed) {
                        if (artifact.source != Source.GooglePlay && artifact.downloadMode != DownloadMode.Unavailable) TextButton(enabled = !busy, onClick = {
                            select(WebChoice(packageName, artifact.versionCode, artifact.source, artifact.packageType,
                                requireNotNull(artifact.metadataUrl), artifact.artifacts.firstOrNull()?.uri,
                                artifact.artifacts.firstOrNull()?.sha256, artifact.artifacts.firstOrNull()?.sizeBytes,
                                assessment.entry.channel))
                        }) { Text("Elegir esta variante") }
                        TextButton(enabled = !busy && playConnected, onClick = {
                            onPlayVersion(packageName, artifact.versionCode)
                        }) { Text("Solicitar primero en Play") }
                    }
                }
                if (maxOf(selection.assessments.size, releases.size, variants.size) > shown) {
                    TextButton(onClick = { shown += 50 }) { Text("Mostrar más versiones") }
                }
            }
            choice?.let { selected ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Descargar o importar", style = MaterialTheme.typography.titleMedium)
                Text("Selección: ${selected.packageName} · ${selected.source} · ${selected.type}", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(19) },
                    label = { Text("Código de versión que debe tener el archivo") }, singleLine = true,
                    enabled = !busy && selected.versionCode == null, modifier = Modifier.fillMaxWidth())
                val expectedCode = code.toLongOrNull()?.takeIf { it > 0 }
                Button(enabled = !busy && expectedCode != null, onClick = {
                    act(selected.source) {
                        download(selected, requireNotNull(expectedCode))
                    }
                }) { Text("Descargar y verificar") }
                Button(enabled = !busy && expectedCode != null, onClick = {
                    assisted = selected.copy(versionCode = expectedCode)
                }) { Text("Continuar en web asistida") }
                TextButton(enabled = !busy, onClick = { browser(selected.url, selected.source) }) { Text("Continuar en navegador") }
                Button(enabled = !busy && expectedCode != null, onClick = {
                    pendingImport = selected.copy(versionCode = expectedCode)
                    picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "application/zip", "*/*"))
                }) { Text("Seleccionar archivo descargado") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (message.isNotBlank()) Text(message)
        }
    }
}
