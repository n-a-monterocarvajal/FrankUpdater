/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.n_a_monterocarvajal.frankupdater.device.GenericDeviceProfileProvider
import io.github.n_a_monterocarvajal.frankupdater.inventory.InstalledAppRepository
import io.github.n_a_monterocarvajal.frankupdater.updates.PackageDownloads
import io.github.n_a_monterocarvajal.frankupdater.updates.UpdatePreferences
import io.github.n_a_monterocarvajal.frankupdater.updates.UpdateObservation
import io.github.n_a_monterocarvajal.frankupdater.updates.UpdateSchedule
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.DateFormat
import java.util.Date

/** Inventory and selection (F-48): its own destination; checking hands over to Updates. */
@Composable
internal fun AppsRoute(repository: InstalledAppRepository, device: GenericDeviceProfileProvider,
    onChecking: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember { UpdatePreferences(context) }
    var selectedPackages by remember { mutableStateOf(preferences.packages) }
    fun saveSelection(packages: Set<String>) {
        preferences.packages = packages
        selectedPackages = packages
    }
    InventoryRoute(
        installedAppRepository = repository,
        deviceProfileProvider = device,
        selectedPackages = selectedPackages,
        onSelectedPackagesChange = ::saveSelection,
        onCheckUpdates = { packages ->
            saveSelection(packages)
            UpdateSchedule.checkNow(context)
            onChecking()
        },
        modifier = modifier,
    )
}

@Composable
internal fun UpdatesRoute(onOpenPackage: (String) -> Unit, onChooseApps: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember { UpdatePreferences(context) }
    var refreshed by remember { mutableIntStateOf(0) }
    var autoUpdate by remember { mutableStateOf(preferences.autoUpdate) }
    // Pending installs that Android still wants confirmed are announced by notification.
    val notifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    // Follow the check job (manual or periodic): show its progress and reload results when it ends.
    val running by remember { androidx.work.WorkManager.getInstance(context) }
        .getWorkInfosFlow(androidx.work.WorkQuery.fromUniqueWorkNames(UpdateSchedule.MANUAL, UpdateSchedule.PERIODIC))
        .collectAsState(emptyList())
    val active = running.firstOrNull { it.state == androidx.work.WorkInfo.State.RUNNING }
    val finishedCount = running.count { it.state.isFinished || it.state == androidx.work.WorkInfo.State.ENQUEUED }
    LaunchedEffect(finishedCount, active == null) { if (active == null) refreshed++ }
    val rows = remember(refreshed) { preferences.observations() }
    var askedNotifications by remember { mutableStateOf(false) }
    fun update(row: UpdateObservation) {
        row.download?.let { PackageDownloads.enqueue(context, it) }
        // Asked once per visit: with the app on screen the install prompt opens without notifications anyway.
        if (android.os.Build.VERSION.SDK_INT >= 33 && !askedNotifications) {
            askedNotifications = true
            notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Rows the last check found behind; enqueue keeps jobs already running and installed rows fail fast on verify.
    // Re-read installed versions whenever a card's download job changes state (an install finished, one failed).
    val jobs by remember(rows) {
        if (rows.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList()) else androidx.work.WorkManager.getInstance(context)
            .getWorkInfosFlow(androidx.work.WorkQuery.fromUniqueWorkNames(rows.map { PackageDownloads.workName(it.packageName) }))
    }.collectAsState(emptyList())
    val installedCodes = remember(rows, refreshed, jobs.map { it.state }) {
        io.github.n_a_monterocarvajal.frankupdater.inventory.AndroidInstalledAppRepository(context)
            .let { repository -> rows.associate { it.packageName to repository.versionCode(it.packageName) } }
    }
    val pending = rows.filter { row ->
        row.download != null && row.available != null && (installedCodes[row.packageName] ?: Long.MAX_VALUE) < row.available
    }
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            UiSection(
                title = "Comprobaciones de actualizaciones",
                supporting = "APKPure, APKMirror, F-Droid e IzzyOnDroid consultan los paquetes elegidos. Todo archivo se verifica antes de instalarse.",
            ) {
                if (preferences.checkedAt > 0) {
                    Text(
                        "Última consulta: ${DateFormat.getDateTimeInstance().format(Date(preferences.checkedAt))}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (active != null) {
                    val done = active.progress.getInt("done", 0)
                    val total = active.progress.getInt("total", 0)
                    if (total > 0) LinearProgressIndicator({ done.toFloat() / total }, Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (total > 0) "Comprobando ${done + 1} de $total…" else "Comprobando…",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pending.size > 1) Button(onClick = { pending.forEach(::update) }) { Text("Actualizar todas (${pending.size})") }
                        // "Comprobar ahora" is the main action unless there are several updates waiting.
                        if (pending.size > 1) OutlinedButton(enabled = preferences.packages.isNotEmpty(), onClick = { UpdateSchedule.checkNow(context) }) { Text("Comprobar ahora") }
                        else Button(enabled = preferences.packages.isNotEmpty(), onClick = { UpdateSchedule.checkNow(context) }) { Text("Comprobar ahora") }
                    }
                }
                if (preferences.packages.isEmpty()) {
                    Text("Todavía no hay apps elegidas para comprobar.", color = MaterialTheme.colorScheme.tertiary)
                }
                TextButton(onClick = onChooseApps) {
                    Text(if (preferences.packages.isEmpty()) "Elegir apps" else "Cambiar apps elegidas (${preferences.packages.size})")
                }
            }
        }
        items(rows, key = { it.packageName }) { row ->
            UpdateResultCard(row, row.packageName in autoUpdate, onAutoUpdate = { enabled ->
                preferences.autoUpdate = if (enabled) preferences.autoUpdate + row.packageName else preferences.autoUpdate - row.packageName
                autoUpdate = preferences.autoUpdate
                if (enabled && !preferences.enabled) { preferences.enabled = true; UpdateSchedule.configure(context) }
                if (enabled && android.os.Build.VERSION.SDK_INT >= 33) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }, onUpdate = { update(row) }) { onOpenPackage(row.packageName) }
        }
    }
}

/** Installed state read now, not at check time, so a finished update shows as such (F-37). */
private class InstalledNow(val icon: androidx.compose.ui.graphics.ImageBitmap?, val versionCode: Long?, val versionName: String?)

/** State of the card's download job, if any, in the words the card shows. */
private fun downloadStatus(job: androidx.work.WorkInfo?): String? = when (job?.state) {
    null, androidx.work.WorkInfo.State.CANCELLED -> null
    androidx.work.WorkInfo.State.ENQUEUED, androidx.work.WorkInfo.State.BLOCKED -> "En cola; espera conexión."
    androidx.work.WorkInfo.State.RUNNING -> {
        val bytes = job.progress.getLong("bytes", 0)
        val total = job.progress.getLong("total", -1)
        when {
            job.progress.getBoolean("verifying", false) -> "Verificando e instalando…"
            total > 0 -> "Descargando ${bytes * 100 / total} % de ${formatBytes(total)}…"
            else -> "Descargando…"
        }
    }
    // A finished install shows as "Actualizada"; other successes (a Search download kept in Library) are not news here.
    androidx.work.WorkInfo.State.SUCCEEDED -> "Confirma la instalación en la notificación de Android."
        .takeIf { job.outputData.getString("message")?.startsWith("Esperando") == true }
    androidx.work.WorkInfo.State.FAILED -> "No se pudo actualizar: ${job.outputData.getString("message") ?: "error desconocido"}"
}

@Composable
private fun UpdateResultCard(row: UpdateObservation, automatic: Boolean, onAutoUpdate: (Boolean) -> Unit,
    onUpdate: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val jobs by remember(row.packageName) {
        androidx.work.WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(PackageDownloads.workName(row.packageName))
    }.collectAsState(emptyList())
    val latest = jobs.lastOrNull()
    val busy = latest != null && !latest.state.isFinished
    // Re-read the installed version when a job changes state, so a finished update shows as such.
    val installed by produceState<InstalledNow?>(null, row.packageName, latest?.state) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val manager = context.packageManager
                @Suppress("DEPRECATION") val info = manager.getPackageInfo(row.packageName, 0)
                val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
                InstalledNow(runCatching { manager.getApplicationIcon(row.packageName).toBitmap(96, 96)
                    .asImageBitmap() }.getOrNull(), code, info.versionName)
            }.getOrNull()
        }
    }
    val current = installed
    val upToDate = row.available != null && current?.versionCode != null && current.versionCode >= row.available
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            current?.icon?.let { androidx.compose.foundation.Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
                ?: Spacer(Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text(row.label ?: row.packageName, style = MaterialTheme.typography.titleMedium)
                if (row.label != null) Text(row.packageName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val installedText = current?.versionName ?: row.installedName ?: row.installed.toString()
                Text(when {
                    upToDate -> "Actualizada a $installedText"
                    row.available != null -> "$installedText → ${row.availableName ?: row.available}" + (row.source?.let { " · $it" } ?: "")
                    else -> "Instalada: $installedText"
                }, style = MaterialTheme.typography.bodyLarge)
                if (!upToDate) Text(row.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val status = downloadStatus(latest)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                if (status != null && (busy || row.available != null && !upToDate)) Text(status, style = MaterialTheme.typography.bodyMedium,
                    color = if (latest?.state == androidx.work.WorkInfo.State.FAILED) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary)
                if (row.available != null && !upToDate) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (row.download != null) {
                        if (busy) OutlinedButton(onClick = { PackageDownloads.cancel(context, row.packageName) }) { Text("Cancelar") }
                        else Button(onClick = onUpdate) { Text("Actualizar") }
                    }
                    TextButton(onClick = onOpen) { Text("Otra versión") }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Actualizar automáticamente", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(automatic, onCheckedChange = onAutoUpdate)
                }
                if (automatic) Text("Se descarga e instala sola cuando hay una versión compatible con la firma confirmada; " +
                    "comprobación cada 24 h.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
