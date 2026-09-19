/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.ui

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.n_a_monterocarvajal.frankupdater.compatibility.*
import io.github.n_a_monterocarvajal.frankupdater.model.*
import io.github.n_a_monterocarvajal.frankupdater.sources.*
import io.github.n_a_monterocarvajal.frankupdater.inventory.AndroidInstalledAppRepository
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

/**
 * Search state and running operations outlive the Search tab: switching tabs neither cancels a download nor
 * clears the query. ponytail: process lifetime only; a foreground WorkManager download (with notification) is the
 * upgrade if downloads must survive the app being backgrounded and killed.
 */
private object WebSearch {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val client = WebSourceClient()
    val packageName = mutableStateOf("")
    val mirrorUrl = mutableStateOf("")
    val releases = mutableStateOf<List<WebRelease>>(emptyList())
    val variants = mutableStateOf<List<MirrorVariant>>(emptyList())
    val choice = mutableStateOf<WebChoice?>(null)
    val code = mutableStateOf("")
    val busy = mutableStateOf(false)
    val job = mutableStateOf<Job?>(null)
    val progress = mutableStateOf<Pair<Long, Long>?>(null)
    val message = mutableStateOf("")
    val failedSources = mutableStateOf<Set<Source>>(emptySet())
    val variantDetails = mutableStateOf<Map<String, MirrorVariantDetails>>(emptyMap())
    val installedSigners = mutableStateOf<Set<String>>(emptySet())
}

@Composable
internal fun WebSourcesCard(
    pipeline: LocalPackagePipeline, library: LocalPackageLibrary, device: GenericDeviceProfile?,
    entries: List<CatalogEntry>, requestedPackage: String?, onPackageConsumed: () -> Unit,
    onEntries: (List<CatalogEntry>) -> Unit,
    onVerified: (CatalogEntry) -> Unit,
    playConnected: Boolean, onPlayVersion: (String, Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = WebSearch.scope
    val client = WebSearch.client
    var packageName by WebSearch.packageName
    var mirrorUrl by WebSearch.mirrorUrl
    var releases by WebSearch.releases
    var variants by WebSearch.variants
    var choice by WebSearch.choice
    var code by WebSearch.code
    var busy by WebSearch.busy
    var job by WebSearch.job
    var progress by WebSearch.progress
    var includePreviews by rememberSaveable { mutableStateOf(false) }
    var message by WebSearch.message
    var failedSources by WebSearch.failedSources
    var pendingImport by remember { mutableStateOf<WebChoice?>(null) }
    var assisted by remember { mutableStateOf<WebChoice?>(null) }
    var shown by remember(packageName, releases, variants, entries) { mutableIntStateOf(50) }
    var variantDetails by WebSearch.variantDetails
    var installedSigners by WebSearch.installedSigners
    LaunchedEffect(variants, variantDetails, packageName) {
        if (variants.isNotEmpty()) onEntries(variants.mapNotNull { it.catalogEntry(packageName, variantDetails[it.url]) })
    }

    /** Blocking: reads each variant page for minSdk, targetSdk, ABIs, size and hash. A failed page stays unknown. */
    fun loadVariants(list: List<MirrorVariant>) {
        variantDetails = list.take(12).mapNotNull { variant ->
            runCatching { variant.url to MirrorParser.variantDetails(client.text(variant.url, Source.ApkMirror), variant.url) }
                .getOrNull()
        }.toMap()
        variants = list
        // A manually consulted APKMirror app that carries this package becomes the feed for later checks.
        list.firstOrNull { variantDetails[it.url]?.packageName == packageName }?.let { variant ->
            MirrorAppStore(context).remember(packageName, MirrorParser.appUrl(MirrorParser.appUrl(variant.url)))
        }
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
                var shownAt = 0L
                client.download(url, selected.source, File(directory, "download.$extension"), headers, selected.sha256, selected.sizeBytes) {
                    bytes, total ->
                    // Throttle state writes: one per 256 KiB is enough for a smooth bar.
                    if (bytes - shownAt >= 262_144 || bytes == total) { shownAt = bytes; progress = bytes to total }
                }
            }
            progress = null
            message = "Descarga completa. Verificando paquete…"
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
        } finally {
            progress = null
            withContext(Dispatchers.IO + NonCancellable) { directory.deleteRecursively() }
        }
    }

    fun act(source: Source, action: suspend () -> Unit) {
        job = scope.launch {
            busy = true
            message = ""
            try { action(); failedSources = failedSources - source }
            catch (cancelled: CancellationException) { message = "Operación cancelada."; throw cancelled }
            catch (error: Exception) {
                // Cancelling interrupts OkHttp, which surfaces as InterruptedIOException rather than cancellation.
                if (!isActive) { message = "Operación cancelada."; return@launch }
                Log.w("FrankUpdater", "Web source operation failed", error)
                failedSources = failedSources + source
                message = "No se pudo completar la operación (${failureReason(error)}). Puedes continuar en la web " +
                    "e importar el archivo; su paquete, versión y firma se comprobarán antes de conservarlo."
            } finally { busy = false; job = null }
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

    fun consultPure() {
        act(Source.ApkPure) {
            requirePackageName(packageName)
            val requested = packageName
            val mirrorApps = MirrorAppStore(context)
            val signers = AndroidInstalledAppRepository(context).signers(requested)
            installedSigners = signers
            val lookup = runInterruptible(Dispatchers.IO) {
                lookupSources(client, requested, requireNotNull(device).sdk, signers, mirrorApps[requested])
            }
            mirrorApps.remember(requested, lookup)
            failedSources = lookup.failedSources
            onEntries(lookup.entries)
            message = if (lookup.entries.isEmpty()) "Ninguna fuente tiene este paquete con una firma compatible."
                else "Fuentes consultadas: " + lookup.entries.map { it.artifact.source.label }.distinct().joinToString() +
                    ". Revisa versión y variante antes de descargar."
        }
    }

    // An update result opened this screen: query its history right away instead of asking to retype the package.
    LaunchedEffect(requestedPackage, device) {
        val requested = requestedPackage ?: return@LaunchedEffect
        if (device == null) return@LaunchedEffect
        packageName = requested; releases = emptyList(); variants = emptyList(); choice = null; failedSources = emptySet()
        onPackageConsumed()
        consultPure()
    }

    val panel = remember { BringIntoViewRequester() }
    LaunchedEffect(choice) { if (choice != null) { withFrameNanos { }; panel.bringIntoView() } }

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
                keyboardOptions = literalKeyboard(KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && device != null && packageName.isNotBlank(), onClick = ::consultPure) {
                Text("Consultar fuentes")
            }
            OutlinedTextField(mirrorUrl, { mirrorUrl = it.take(2048); releases = emptyList(); variants = emptyList(); choice = null },
                label = { Text("Página de aplicación o release en APKMirror") }, singleLine = true, enabled = !busy,
                keyboardOptions = literalKeyboard(KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && packageName.isNotBlank() && mirrorUrl.isNotBlank(), onClick = {
                act(Source.ApkMirror) {
                    requirePackageName(packageName)
                    runInterruptible(Dispatchers.IO) {
                        val html = client.text(mirrorUrl, Source.ApkMirror)
                        if (sourceUrl(mirrorUrl, Source.ApkMirror).encodedPath.contains("-release/")) {
                            loadVariants(MirrorParser.variants(html, mirrorUrl)); releases = emptyList()
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
                        runInterruptible(Dispatchers.IO) { loadVariants(MirrorParser.variants(client.text(release.url, Source.ApkMirror), release.url)) }
                        releases = emptyList()
                    }
                }) { Text(release.name) }
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
                    } + " · " + when {
                        installedSigners.isEmpty() -> "app no instalada"
                        artifact.signerDigests.any(installedSigners::contains) -> "firma coincide con la instalada"
                        else -> "firma sin confirmar hasta descargar"
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (assessment.incompatibilities.isNotEmpty()) {
                        Text("No compatible: ${assessment.incompatibilities.joinToString()}", color = MaterialTheme.colorScheme.error)
                    }
                    else if (assessment.channelAllowed) {
                        if (artifact.source != Source.GooglePlay && artifact.downloadMode != DownloadMode.Unavailable) TextButton(enabled = !busy, onClick = {
                            select(WebChoice(packageName, artifact.versionCode, artifact.source, artifact.packageType,
                                requireNotNull(artifact.metadataUrl),
                                artifact.artifacts.firstOrNull()?.uri?.takeIf { artifact.downloadMode == DownloadMode.Direct },
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
            choice?.let { selected -> Column(Modifier.bringIntoViewRequester(panel), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                progress?.let { (bytes, total) -> DownloadProgress(bytes, total) { job?.cancel() } }
                Button(enabled = !busy && expectedCode != null, onClick = {
                    assisted = selected.copy(versionCode = expectedCode)
                }) { Text("Continuar en web asistida") }
                TextButton(enabled = !busy, onClick = { browser(selected.url, selected.source) }) { Text("Continuar en navegador") }
                Button(enabled = !busy && expectedCode != null, onClick = {
                    pendingImport = selected.copy(versionCode = expectedCode)
                    picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "application/zip", "*/*"))
                }) { Text("Seleccionar archivo descargado") }
            } }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (message.isNotBlank()) Text(message)
        }
    }
}

@Composable
private fun DownloadProgress(bytes: Long, total: Long, onCancel: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (total > 0) LinearProgressIndicator({ (bytes.toFloat() / total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
        else LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(Formatter.formatShortFileSize(context, bytes) +
                (if (total > 0) " de " + Formatter.formatShortFileSize(context, total) else ""),
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }
    }
}

private fun failureReason(error: Exception): String = when (error) {
    is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> "la fuente dejó de enviar datos"
    is java.net.UnknownHostException -> "sin conexión"
    is java.io.IOException -> error.message ?: "error de red"
    is IllegalArgumentException, is IllegalStateException -> "el archivo no coincide con lo esperado"
    else -> error.javaClass.simpleName
}

/** Package names and URLs are identifiers: no autocorrection or capitalization from the IME. */
internal fun literalKeyboard(type: KeyboardType) =
    KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = type)
