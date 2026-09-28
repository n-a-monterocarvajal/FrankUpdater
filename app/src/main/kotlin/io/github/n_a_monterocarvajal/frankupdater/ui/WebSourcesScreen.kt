/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.ui

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import io.github.n_a_monterocarvajal.frankupdater.play.PlaySession
import androidx.compose.ui.unit.dp
import io.github.n_a_monterocarvajal.frankupdater.compatibility.*
import io.github.n_a_monterocarvajal.frankupdater.model.*
import io.github.n_a_monterocarvajal.frankupdater.sources.*
import io.github.n_a_monterocarvajal.frankupdater.inventory.AndroidInstalledAppRepository
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackageLibrary
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackagePipeline
import io.github.n_a_monterocarvajal.frankupdater.verification.VerificationExpectations
import io.github.n_a_monterocarvajal.frankupdater.verification.catalogEntry
import kotlinx.coroutines.*
import io.github.n_a_monterocarvajal.frankupdater.updates.DownloadRequest
import io.github.n_a_monterocarvajal.frankupdater.updates.PackageDownloads
import io.github.n_a_monterocarvajal.frankupdater.updates.withVerifiedDownload

private data class WebChoice(val packageName: String, val versionCode: Long?, val source: Source,
    val type: PackageType, val url: String, val directUrl: String? = null,
    val sha256: String? = null, val sizeBytes: Long? = null,
    val channel: ReleaseChannel = ReleaseChannel.Unknown)

private fun WebChoice.toRequest(expectedCode: Long) =
    DownloadRequest(packageName, expectedCode, source, type, url, directUrl, sha256, sizeBytes, channel)

/**
 * Search state and running operations outlive the Search tab: switching tabs neither clears the query nor
 * cancels a lookup. Direct downloads run in PackageDownloadWorker and survive the app; only assisted web downloads
 * (captured cookies) stay in this process scope.
 */
private object WebSearch {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val client = WebSourceClient()
    val packageName = mutableStateOf("")
    val mirrorUrl = mutableStateOf("")
    val releases = mutableStateOf<List<WebRelease>>(emptyList())
    val variants = mutableStateOf<List<MirrorVariant>>(emptyList())
    /** The version whose download routes are open; one at a time. */
    val selectedCode = mutableStateOf<Long?>(null)
    val busy = mutableStateOf(false)
    val job = mutableStateOf<Job?>(null)
    val progress = mutableStateOf<Pair<Long, Long>?>(null)
    val message = mutableStateOf("")
    /** Result of an assisted download or an import, shown inside the open version card. */
    val panelMessage = mutableStateOf("")
    val failedSources = mutableStateOf<Set<Source>>(emptySet())
    val variantDetails = mutableStateOf<Map<String, MirrorVariantDetails>>(emptyMap())
    val installedSigners = mutableStateOf<Set<String>>(emptySet())
    val installedVersion = mutableStateOf<Long?>(null)
    val nameMatches = mutableStateOf<List<NameMatch>>(emptyList())
}

@Composable
internal fun WebSourcesCard(
    pipeline: LocalPackagePipeline, library: LocalPackageLibrary, device: GenericDeviceProfile?,
    entries: List<CatalogEntry>, requestedPackage: String?, onPackageConsumed: () -> Unit,
    onEntries: (List<CatalogEntry>) -> Unit,
    onVerified: (CatalogEntry) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusSink = remember { FocusRequester() }
    val scope = WebSearch.scope
    val client = WebSearch.client
    var packageName by WebSearch.packageName
    var mirrorUrl by WebSearch.mirrorUrl
    var releases by WebSearch.releases
    var variants by WebSearch.variants
    var selectedCode by WebSearch.selectedCode
    var panelMessage by WebSearch.panelMessage
    var busy by WebSearch.busy
    var job by WebSearch.job
    var progress by WebSearch.progress
    var includePreviews by rememberSaveable { mutableStateOf(false) }
    var message by WebSearch.message
    var failedSources by WebSearch.failedSources
    var pendingImport by remember { mutableStateOf<WebChoice?>(null) }
    var assisted by remember { mutableStateOf<WebChoice?>(null) }
    var shown by remember(packageName, releases, variants, entries) { mutableIntStateOf(10) }
    var showIncompatible by remember(packageName) { mutableStateOf(false) }
    var moreOptions by rememberSaveable { mutableStateOf(false) }
    var variantDetails by WebSearch.variantDetails
    var installedSigners by WebSearch.installedSigners
    var installedVersion by WebSearch.installedVersion
    var nameMatches by WebSearch.nameMatches
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

    /** Assisted web downloads only: their captured URL and cookies must not be persisted by WorkManager. */
    suspend fun download(selected: WebChoice, expectedCode: Long, capturedUrl: String, headers: Map<String, String>) {
        try {
            withVerifiedDownload(context, client, pipeline, selected.toRequest(expectedCode), capturedUrl, headers,
                onProgress = { bytes, total -> progress = bytes to total },
                onVerifying = { progress = null; panelMessage = "Descarga completa. Verificando paquete…" }) { prepared ->
                val verifiedEntry = runInterruptible(Dispatchers.IO) {
                    library.retain(prepared)
                    prepared.verified.catalogEntry(selected.source, selected.url, selected.channel)
                }
                onVerified(verifiedEntry)
            }
            panelMessage = "Paquete verificado y guardado en Biblioteca."
        } finally { progress = null }
    }

    /** [inPanel]: report in the open version card (downloads, imports) instead of under the search box. */
    fun act(source: Source, inPanel: Boolean = false, action: suspend () -> Unit) {
        // Park focus on the title: on Android 8 a plain clearFocus hands it back to the first text field, which
        // reopens the keyboard when the operation ends.
        keyboard?.hide(); runCatching { focusSink.requestFocus() }
        fun report(text: String) { if (inPanel) panelMessage = text else message = text }
        job = scope.launch {
            busy = true
            report("")
            try { action(); failedSources = failedSources - source }
            catch (cancelled: CancellationException) { report("Operación cancelada."); throw cancelled }
            catch (error: Exception) {
                // Cancelling interrupts OkHttp, which surfaces as InterruptedIOException rather than cancellation.
                if (!isActive) { report("Operación cancelada."); return@launch }
                Log.w("FrankUpdater", "Web source operation failed", error)
                failedSources = failedSources + source
                report("No se pudo completar la operación (${failureReason(error)}). Puedes continuar en la web " +
                    "e importar el archivo; su paquete, versión y firma se comprobarán antes de conservarlo.")
            } finally { busy = false; job = null }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val expected = pendingImport
        pendingImport = null
        if (uri != null && expected != null) act(expected.source, inPanel = true) {
            pipeline.import(uri, VerificationExpectations(expected.packageName, requireNotNull(expected.versionCode))).use {
                prepared ->
                require(expected.sha256 == null || expected.sha256.equals(prepared.verified.sourceSha256, ignoreCase = true))
                require(expected.sizeBytes == null || expected.sizeBytes == prepared.verified.sourceSizeBytes)
                runInterruptible(Dispatchers.IO) { library.retain(prepared) }
                onVerified(runInterruptible(Dispatchers.IO) {
                    prepared.verified.catalogEntry(expected.source, expected.url, expected.channel)
                })
            }
            panelMessage = "Paquete verificado y guardado en Biblioteca."
        }
    }

    /** Queries every source for [packageName]; also the second step of a name search on APKMirror. */
    suspend fun lookup() {
        run {
            requirePackageName(packageName)
            val requested = packageName
            val mirrorApps = MirrorAppStore(context)
            val repository = AndroidInstalledAppRepository(context)
            val signers = repository.signers(requested)
            installedSigners = signers
            installedVersion = repository.versionCode(requested)
            val lookup = runInterruptible(Dispatchers.IO) {
                lookupSources(client, requested, requireNotNull(device).sdk, signers, mirrorApps[requested], installedVersion)
            }
            mirrorApps.remember(requested, lookup)
            lookup.failures.forEach { (source, reason) -> Log.w("FrankUpdater", "$requested @ $source: $reason") }
            failedSources = lookup.failedSources
            onEntries(lookup.entries)
            val failed = lookup.failedSources.joinToString { it.label }
            message = when {
                lookup.entries.isEmpty() && lookup.failedSources.size == 4 ->
                    "No se pudo consultar ninguna fuente ($failed). Comprueba la conexión."
                lookup.entries.isEmpty() -> "Ninguna fuente tiene este paquete con una firma compatible." +
                    if (failed.isNotEmpty()) " Sin respuesta: $failed." else ""
                else -> "Fuentes consultadas: " + lookup.entries.map { it.artifact.source.label }.distinct().joinToString() +
                    (if (failed.isNotEmpty()) ". Sin respuesta: $failed" else "") +
                    ". Revisa versión y variante antes de descargar."
            }
        }
    }

    fun consultPure() = act(Source.ApkPure) { lookup() }

    // An update result opened this screen: query its history right away instead of asking to retype the package.
    LaunchedEffect(requestedPackage, device) {
        val requested = requestedPackage ?: return@LaunchedEffect
        if (device == null) return@LaunchedEffect
        packageName = requested; releases = emptyList(); variants = emptyList(); selectedCode = null; failedSources = emptySet()
        onPackageConsumed()
        consultPure()
    }

    val panel = remember { BringIntoViewRequester() }
    LaunchedEffect(selectedCode) { if (selectedCode != null) { withFrameNanos { }; panel.bringIntoView() } }
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
                act(selected.source, inPanel = true) { download(selected, requireNotNull(selected.versionCode), url, headers) }
            })
    }

    /** One way to get a version: a web variant or Google Play (unavailable until a Play session exists). */
    data class Route(val key: String, val source: Source, val title: String, val detail: String, val available: Boolean,
        val status: String, val choice: WebChoice?)

    /**
     * The routes of one version as alternatives: pick one, start it, and follow its steps in the same card.
     * Play is a route like the web sources, so its download runs in the background worker too.
     */
    @Composable
    fun VersionRoutes(versionCode: Long, group: List<CatalogAssessment>) {
        // Reads the state so the routes refresh on connect; a saved session also counts (the worker reconnects).
        val playConnected = PlaySession.provider.value != null || PlaySession.available(context)
        val playUrl = "https://play.google.com/store/apps/details?id=$packageName"
        val webRoutes = group.filter {
            it.entry.artifact.source != Source.GooglePlay && it.entry.artifact.downloadMode != DownloadMode.Unavailable
        }.map { assessment ->
            val artifact = assessment.entry.artifact
            val file = artifact.artifacts.firstOrNull()
            Route("${artifact.source}:${artifact.packageType}:${artifact.abis}", artifact.source,
                "${artifact.source.label} · ${artifact.packageType.label}",
                listOfNotNull(artifact.abis.ifEmpty { listOf("todas las ABI") }.joinToString(),
                    file?.sizeBytes?.let { Formatter.formatShortFileSize(context, it) }).joinToString(" · "),
                true, "Disponible",
                WebChoice(packageName, versionCode, artifact.source, artifact.packageType, requireNotNull(artifact.metadataUrl),
                    file?.uri?.takeIf { artifact.downloadMode == DownloadMode.Direct }, file?.sha256, file?.sizeBytes,
                    assessment.entry.channel))
        }.distinctBy { it.key }
        val play = Route("play", Source.GooglePlay, "Google Play",
            if (playConnected) "Play prepara los APK de este dispositivo" else "Conéctalo en la sección Google Play, más abajo",
            playConnected, if (playConnected) "Bajo pedido" else "Sin conectar", null)
        // Sources without this version: one line at the end, so the real alternatives fit on a small screen.
        val (unanswered, lacking) = listOf(Source.ApkMirror, Source.ApkPure, Source.FDroid, Source.IzzyOnDroid)
            .filter { source -> webRoutes.none { it.source == source } }
            .partition { it in failedSources }
        val routes = (listOf(play) + webRoutes).sortedBy { !it.available }
        var routeKey by remember(versionCode) { mutableStateOf(routes.firstOrNull { it.available }?.key) }
        val route = routes.firstOrNull { it.key == routeKey && it.available }

        fun start(target: Route, install: Boolean) {
            panelMessage = ""
            routeKey = target.key
            PackageDownloads.enqueue(context, target.choice?.toRequest(versionCode)?.copy(install = install)
                ?: DownloadRequest(packageName, versionCode, Source.GooglePlay, PackageType.SplitApkSet, playUrl, install = install))
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Vías de descarga: usa una", style = MaterialTheme.typography.titleSmall)
        routes.forEach { item ->
            val chosen = item.key == routeKey && item.available
            OutlinedCard(
                border = androidx.compose.foundation.BorderStroke(if (chosen) 2.dp else 1.dp,
                    if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
                    .selectable(chosen, enabled = item.available, role = Role.RadioButton) { routeKey = item.key },
            ) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(chosen, onClick = null, enabled = item.available)
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall,
                            color = if (item.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                        Text(item.detail, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusBadge(item.status,
                        if (item.available) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        if (item.available) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        if (lacking.isNotEmpty()) Text("Sin este código: " + lacking.joinToString { it.label },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (unanswered.isNotEmpty()) Text("No respondieron en la última consulta: " + unanswered.joinToString { it.label },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        route?.let { target ->
            Button(onClick = { start(target, true) }, modifier = Modifier.fillMaxWidth()) {
                Text("Descargar e instalar desde ${target.source.label}")
            }
            TextButton(onClick = { start(target, false) }) { Text("Solo guardar en Biblioteca") }
        }
        DownloadSteps(packageName, versionCode,
            onRetry = { source, install -> routes.firstOrNull { it.available && it.source == source }?.let { start(it, install) } },
            alternatives = routes.filter { it.available }.map { it.source }.distinct())
        DownloadProgress { job?.cancel() }
        if (panelMessage.isNotBlank()) Text(panelMessage, style = MaterialTheme.typography.bodyMedium)
        Text("Manual", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            route?.choice?.let { chosen ->
                OutlinedButton(enabled = !busy, onClick = { assisted = chosen }) { Text("Web asistida") }
                // APKPure has no stable page per version code: say it opens the history, not "this version".
                TextButton(onClick = { browser(chosen.url, chosen.source) }) {
                    Text(if (chosen.source == Source.ApkPure) "Abrir historial en APKPure" else "Abrir en ${chosen.source.label}")
                }
            }
            TextButton(enabled = !busy, onClick = {
                pendingImport = route?.choice ?: WebChoice(packageName, versionCode, Source.GooglePlay,
                    PackageType.SplitApkSet, playUrl)
                picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "application/zip", "*/*"))
            }) { Text("Importar archivo descargado") }
        }
        TextButton(onClick = { selectedCode = null }) { Text("Cerrar") }
    }

    // Three blocks (F-44): search, advanced options, and results; the download routes open inside a version card.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Buscar apps", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.focusRequester(focusSink).focusable())
            Text(
                "Por nombre o por paquete. Se consultan APKPure, APKMirror, F-Droid e IzzyOnDroid, sin iniciar sesión.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(packageName, {
                packageName = it.take(255); releases = emptyList(); variants = emptyList(); selectedCode = null; failedSources = emptySet()
                nameMatches = emptyList()
            }, label = { Text("Nombre de app o paquete") }, singleLine = true, readOnly = busy,
                keyboardOptions = literalKeyboard(KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth())
            // Name search: installed apps first, then APKMirror for apps that are not installed (D-01).
            val installedApps by produceState(emptyList<Pair<String, String>>()) {
                value = runCatching { AndroidInstalledAppRepository(context).getInstalledApps() }.getOrDefault(emptyList())
                    .filter { !it.isSystemApp }.map { it.displayName to it.packageName }
            }
            val query = packageName.trim()
            val isPackage = remember(query) { runCatching { requirePackageName(query) }.isSuccess }
            if (!isPackage && query.length >= 2) {
                val matches = remember(query, installedApps) {
                    installedApps.filter { (name, id) -> name.contains(query, true) || id.contains(query, true) }.take(5)
                }
                if (matches.isNotEmpty()) Text("Instaladas", style = MaterialTheme.typography.labelLarge)
                matches.forEach { (name, id) ->
                    TextButton(enabled = !busy && device != null, onClick = { packageName = id; consultPure() }) { Text(if (name == id) id else "$name · $id") }
                }
                // Apps that are not installed (D-01): find them by name on APKPure and APKMirror, then query all sources.
                // With a name typed, searching the web is the action; the package button would only sit disabled.
                Button(enabled = !busy && device != null, onClick = {
                    act(Source.ApkPure) {
                        nameMatches = runInterruptible(Dispatchers.IO) { searchAppsByName(client, query, requireNotNull(device).sdk) }
                        if (nameMatches.isEmpty()) message = "APKPure y APKMirror no tienen apps con ese nombre. Prueba otro nombre o el paquete."
                    }
                }) { Text("Buscar \"$query\" en la web") }
                if (nameMatches.isNotEmpty()) Text("En la web", style = MaterialTheme.typography.labelLarge)
                nameMatches.forEach { app ->
                    TextButton(enabled = !busy && device != null, onClick = {
                        act(if (app.packageName != null) Source.ApkPure else Source.ApkMirror) {
                            val id = app.packageName ?: runInterruptible(Dispatchers.IO) { mirrorAppPackage(client, requireNotNull(app.mirrorUrl)) }
                                ?: throw java.io.IOException("APKMirror no indica el paquete de ${app.name}")
                            app.mirrorUrl?.let { MirrorAppStore(context).remember(id, it) }
                            nameMatches = emptyList()
                            packageName = id
                            lookup()
                        }
                    }) { Text(listOfNotNull(app.name, app.developer, app.packageName).joinToString(" · ")) }
                }
            }
            if (isPackage) Button(enabled = !busy && device != null, onClick = ::consultPure) {
                Text("Consultar fuentes")
            }
            // Lookup status next to the button that started it, not at the end of the results.
            if (busy && selectedCode == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Rarely needed, so they start folded: a direct APKMirror page, the APKPure history and previews.
            Row(Modifier.fillMaxWidth().clickable { moreOptions = !moreOptions },
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Opciones avanzadas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (moreOptions) "Ocultar" else "Mostrar", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (moreOptions) {
            OutlinedTextField(mirrorUrl, { mirrorUrl = it.take(2048); releases = emptyList(); variants = emptyList(); selectedCode = null },
                label = { Text("Página de aplicación o release en APKMirror") }, singleLine = true, readOnly = busy,
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
            device?.let {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(includePreviews, onCheckedChange = { includePreviews = it })
                    Text("Incluir versiones preliminares (alpha/beta/RC)")
                }
            }
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (releases.isNotEmpty() || variants.isNotEmpty() || entries.any { it.artifact.packageName == packageName }) {
                Text("Versiones encontradas", style = MaterialTheme.typography.titleMedium)
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
                // Selection is pure but not cheap: recompute only when its inputs change, not on every progress tick.
                val selection = remember(packageName, entries, profile, failedSources, includePreviews) {
                    VersionCatalog().select(packageName.ifBlank { "unknown.app" },
                        entries.filter { it.artifact.packageName == packageName }, profile, unavailableSources = failedSources,
                        includePreviews = includePreviews)
                }
                val (compatible, incompatible) = remember(selection) {
                    selection.assessments.distinctBy { assessment ->
                        assessment.entry.artifact.let { listOf(it.versionCode, it.source, it.abis, it.packageType) }
                    }.partition { it.incompatibilities.isEmpty() }
                }
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
                val rows = if (showIncompatible) compatible + incompatible else compatible
                val installed = installedVersion
                // The version to pick: newest compatible, allowed channel, above the installed one.
                val recommendedCode = selection.latestCompatibleVersionCode?.takeIf { installed == null || it > installed }
                // One card per version code; its variants and Google Play are the routes inside it, not separate rows.
                val versions = rows.groupBy { it.entry.artifact.versionCode }.toList()
                versions.take(shown).forEach { (versionCode, group) ->
                    val first = group.first()
                    val artifact = first.entry.artifact
                    val isRecommended = versionCode == recommendedCode && first.incompatibilities.isEmpty() && first.channelAllowed
                    val isOpen = selectedCode == versionCode
                    val signer = when {
                        installedSigners.isEmpty() -> null
                        group.any { it.entry.artifact.signerDigests.any(installedSigners::contains) } -> true
                        else -> false
                    }
                    OutlinedCard(
                        colors = if (isRecommended) CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.outlinedCardColors(),
                        modifier = Modifier.fillMaxWidth().then(if (isOpen) Modifier.bringIntoViewRequester(panel) else Modifier),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(artifact.versionName ?: versionCode.toString(), style = MaterialTheme.typography.titleMedium)
                                    Text("código $versionCode · " + group.flatMap { it.entry.artifact.abis }.distinct()
                                        .ifEmpty { listOf("todas las ABI") }.joinToString(),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isRecommended) StatusBadge("Recomendada", MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.onPrimary)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val neutral = MaterialTheme.colorScheme.surfaceVariant
                                val onNeutral = MaterialTheme.colorScheme.onSurfaceVariant
                                StatusBadge(when (first.entry.channel) {
                                    ReleaseChannel.Preview -> "Preliminar"
                                    ReleaseChannel.Stable -> "Estable"
                                    ReleaseChannel.Unknown -> "Canal sin confirmar"
                                }, neutral, onNeutral)
                                when (signer) {
                                    true -> StatusBadge("Firma coincide", MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.colorScheme.onSecondaryContainer)
                                    false -> StatusBadge("Firma sin confirmar", neutral, onNeutral)
                                    null -> {}
                                }
                                when {
                                    first.incompatibilities.isNotEmpty() -> StatusBadge(
                                        "No compatible: " + first.incompatibilities.joinToString { it.label },
                                        MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                    installed != null && versionCode < installed ->
                                        StatusBadge("Inferior a la instalada", neutral, onNeutral)
                                    installed != null && versionCode == installed -> StatusBadge("Instalada",
                                        MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                            }
                            // Android refuses downgrades and verification rejects them, so only newer versions get routes.
                            val actionable = first.incompatibilities.isEmpty() && first.channelAllowed &&
                                (installed == null || versionCode > installed)
                            if (actionable && !isOpen) {
                                val choose = { selectedCode = versionCode; panelMessage = "" }
                                if (isRecommended) Button(onClick = choose) { Text("Elegir esta versión") }
                                else OutlinedButton(onClick = choose) { Text("Elegir esta versión") }
                            }
                            if (actionable && isOpen) VersionRoutes(versionCode, group)
                        }
                    }
                }
                if (versions.size > shown) TextButton(onClick = { shown += 20 }) { Text("Mostrar más versiones") }
                if (incompatible.isNotEmpty()) TextButton(onClick = { showIncompatible = !showIncompatible }) {
                    Text(if (showIncompatible) "Ocultar las no compatibles" else "Mostrar ${incompatible.size} no compatibles")
                }
            }
    }
    }
}

@Composable
private fun DownloadProgress(onCancel: () -> Unit) {
    // Reads the progress state here so each tick recomposes only this bar, not the whole Search card.
    val (bytes, total) = WebSearch.progress.value ?: return
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

private val PackageType.label: String get() = when (this) {
    PackageType.MonolithicApk -> "APK"
    PackageType.SplitApkSet -> "APKS"
    PackageType.Apkm -> "APKM"
    PackageType.Xapk -> "XAPK"
}

private val IncompatibilityReason.label: String get() = when (this) {
    IncompatibilityReason.MinSdk -> "requiere una versión de Android más nueva"
    IncompatibilityReason.MaxSdk -> "no admite esta versión de Android"
    IncompatibilityReason.TargetSdk -> "targetSdk demasiado antiguo para instalarse"
    IncompatibilityReason.Abi -> "arquitectura no compatible"
    IncompatibilityReason.RequiredFeature -> "falta una función de hardware requerida"
}

@Composable
private fun StatusBadge(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
