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
import io.github.n_a_monterocarvajal.frankupdater.updates.UpdateSchedule
import java.text.DateFormat
import java.util.Date

@Composable
internal fun UpdatesRoute(repository: InstalledAppRepository, device: GenericDeviceProfileProvider, modifier: Modifier = Modifier) {
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
                        supporting = "APKPure consulta los paquetes elegidos en Ajustes. Todo archivo se verifica antes de instalarse.",
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
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(row.packageName, style = MaterialTheme.typography.titleMedium)
                            Text("Instalada: ${row.installed} · Disponible: ${row.available ?: "Sin confirmar"}")
                            Text(row.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Consulta este paquete en Buscar para descargar y verificar la versión.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
