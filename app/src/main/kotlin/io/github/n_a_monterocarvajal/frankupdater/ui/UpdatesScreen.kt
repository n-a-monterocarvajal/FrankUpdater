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
import io.github.n_a_monterocarvajal.frankupdater.updates.UpdatePreferences
import io.github.n_a_monterocarvajal.frankupdater.updates.UpdateObservation
import io.github.n_a_monterocarvajal.frankupdater.updates.UpdateSchedule
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.DateFormat
import java.util.Date

@Composable
internal fun UpdatesRoute(repository: InstalledAppRepository, device: GenericDeviceProfileProvider,
    onOpenPackage: (String) -> Unit, modifier: Modifier = Modifier) {
    var checks by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val preferences = remember { UpdatePreferences(context) }
    var refreshed by remember { mutableIntStateOf(0) }
    var checkRequested by rememberSaveable { mutableStateOf(false) }
    var selectedPackages by remember { mutableStateOf(preferences.packages) }
    fun saveSelection(packages: Set<String>) {
        preferences.packages = packages
        selectedPackages = packages
    }
    Column(modifier.fillMaxSize()) {
        TextButton(onClick = { checks = !checks }) { Text(if (checks) "Volver a seleccionar aplicaciones" else "Ver resultados de comprobaciones") }
        if (!checks) InventoryRoute(
            installedAppRepository = repository,
            deviceProfileProvider = device,
            selectedPackages = selectedPackages,
            onSelectedPackagesChange = ::saveSelection,
            onCheckUpdates = { packages ->
                saveSelection(packages)
                UpdateSchedule.checkNow(context)
                refreshed++
                checkRequested = true
                checks = true
            },
            modifier = Modifier.weight(1f),
        )
        else {
            val rows = remember(refreshed) { preferences.observations() }
            LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Button(enabled = preferences.packages.isNotEmpty(), onClick = { UpdateSchedule.checkNow(context) }) { Text("Comprobar ahora") }
                        TextButton(onClick = { refreshed++ }) { Text("Actualizar resultados") }
                        if (checkRequested) {
                            Text("Comprobación iniciada. Actualiza los resultados cuando termine.", color = MaterialTheme.colorScheme.primary)
                        }
                        if (preferences.packages.isEmpty()) {
                            Text("Selecciona paquetes en Ajustes para comenzar.", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                items(rows, key = { it.packageName }) { row ->
                    UpdateResultCard(row) { onOpenPackage(row.packageName) }
                }
            }
        }
    }
}

/** Installed state read now, not at check time, so a finished update shows as such (F-37). */
private class InstalledNow(val icon: androidx.compose.ui.graphics.ImageBitmap?, val versionCode: Long?, val versionName: String?)

@Composable
private fun UpdateResultCard(row: UpdateObservation, onOpen: () -> Unit) {
    val context = LocalContext.current
    val installed by produceState<InstalledNow?>(null, row.packageName) {
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
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
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
                if (row.available != null && !upToDate) Text("Toca para consultar fuentes, descargar y verificar.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
