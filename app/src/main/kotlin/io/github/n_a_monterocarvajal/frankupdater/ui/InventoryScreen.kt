/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Inventory filtering and update-selection flow adapted from APKUpdater's
 * AppsScreen, AppsViewModel and UpdatesRepository at 69b6fcdf52a7735ae17101efe1a0cd26222fb276.
 */
package io.github.n_a_monterocarvajal.frankupdater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.n_a_monterocarvajal.frankupdater.device.GenericDeviceProfileProvider
import io.github.n_a_monterocarvajal.frankupdater.inventory.InstalledAppRepository
import io.github.n_a_monterocarvajal.frankupdater.model.GenericDeviceProfile
import io.github.n_a_monterocarvajal.frankupdater.model.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface InventoryUiState {
    data object Loading : InventoryUiState

    data class Content(
        val apps: List<InstalledApp>,
        val device: GenericDeviceProfile,
    ) : InventoryUiState

    data class Error(val cause: Throwable) : InventoryUiState
}

internal enum class InventoryFilter { All, User, System }

internal fun filterInstalledApps(
    apps: List<InstalledApp>,
    query: String,
    filter: InventoryFilter,
): List<InstalledApp> = apps.filter { app ->
    (filter == InventoryFilter.All || app.isSystemApp == (filter == InventoryFilter.System)) &&
        (query.isBlank() || app.displayName.contains(query, ignoreCase = true) ||
            app.packageName.contains(query, ignoreCase = true))
}

@Composable
internal fun InventoryRoute(
    installedAppRepository: InstalledAppRepository,
    deviceProfileProvider: GenericDeviceProfileProvider,
    selectedPackages: Set<String> = emptySet(),
    onSelectedPackagesChange: (Set<String>) -> Unit = {},
    onCheckUpdates: (Set<String>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var reloadToken by rememberSaveable { mutableIntStateOf(0) }
    val state by produceState<InventoryUiState>(
        initialValue = InventoryUiState.Loading,
        key1 = installedAppRepository,
        key2 = deviceProfileProvider,
        key3 = reloadToken,
    ) {
        value = InventoryUiState.Loading
        value = try {
            withContext(Dispatchers.IO) {
                InventoryUiState.Content(
                    apps = installedAppRepository.getInstalledApps(),
                    device = deviceProfileProvider.getDeviceProfile(),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            InventoryUiState.Error(error)
        }
    }

    when (val currentState = state) {
        InventoryUiState.Loading -> LoadingInventory(modifier)
        is InventoryUiState.Content -> InventoryContent(
            state = currentState,
            onReload = { reloadToken += 1 },
            selectedPackages = selectedPackages,
            onSelectedPackagesChange = onSelectedPackagesChange,
            onCheckUpdates = onCheckUpdates,
            modifier = modifier,
        )
        is InventoryUiState.Error -> InventoryError(
            cause = currentState.cause,
            onRetry = { reloadToken += 1 },
            modifier = modifier,
        )
    }
}

@Composable
private fun LoadingInventory(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = "Leyendo aplicaciones instaladas…",
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun InventoryError(
    cause: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No se pudo leer el inventario",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = cause.message ?: "Error desconocido",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
private fun InventoryContent(
    state: InventoryUiState.Content,
    onReload: () -> Unit,
    selectedPackages: Set<String>,
    onSelectedPackagesChange: (Set<String>) -> Unit,
    onCheckUpdates: (Set<String>) -> Unit,
    modifier: Modifier,
) {
    val systemCount = state.apps.count(InstalledApp::isSystemApp)
    val userCount = state.apps.size - systemCount
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(InventoryFilter.User) }
    val visibleApps = filterInstalledApps(state.apps, query, filter)
    val visibleSelection = selectedPackages.intersect(visibleApps.map(InstalledApp::packageName).toSet())

    // Summary, search and filters scroll away with the list; the selection actions stay pinned at the bottom.
    Column(modifier = modifier) {
        if (state.apps.isEmpty()) {
            DeviceSummary(state.device, state.apps.size, userCount, systemCount, onReload)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay aplicaciones visibles en este dispositivo.")
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeviceSummary(state.device, state.apps.size, userCount, systemCount, onReload)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Buscar por nombre o paquete") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InventoryFilter.entries.forEach { option ->
                            FilterChip(
                                selected = filter == option,
                                onClick = { filter = option },
                                label = { Text(when (option) {
                                    InventoryFilter.All -> "Todas"
                                    InventoryFilter.User -> "Usuario"
                                    InventoryFilter.System -> "Sistema"
                                }) },
                            )
                        }
                    }
                    Text("${visibleApps.size} visibles · ${selectedPackages.size} seleccionadas (máximo 50)",
                        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
                    if (visibleApps.isEmpty()) Text("No hay aplicaciones que coincidan con el filtro.",
                        Modifier.padding(horizontal = 16.dp, vertical = 24.dp))
                }
            }
            items(
                items = visibleApps,
                key = InstalledApp::packageName,
            ) { app ->
                InstalledAppCard(
                    app = app,
                    selected = app.packageName in selectedPackages,
                    selectionEnabled = app.packageName in selectedPackages || selectedPackages.size < 50,
                    onSelectedChange = { checked ->
                        onSelectedPackagesChange(if (checked) selectedPackages + app.packageName
                            else selectedPackages - app.packageName)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    enabled = visibleApps.isNotEmpty() && selectedPackages.size < 50,
                    onClick = {
                        onSelectedPackagesChange(selectedPackages + visibleApps
                            .map(InstalledApp::packageName)
                            .filterNot(selectedPackages::contains)
                            .take(50 - selectedPackages.size))
                    },
                ) { Text("Seleccionar ${visibleApps.size}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Button(
                    enabled = visibleSelection.isNotEmpty(),
                    onClick = { onCheckUpdates(visibleSelection) },
                ) { Text("Comprobar (${visibleSelection.size})") }
            }
        }
    }
}

@Composable
private fun DeviceSummary(
    device: GenericDeviceProfile,
    appCount: Int,
    userCount: Int,
    systemCount: Int,
    onReload: () -> Unit,
) {
    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$appCount aplicaciones · $userCount de usuario · $systemCount de sistema",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append("Android ")
                        append(device.androidRelease ?: device.sdk)
                        append(" · API ")
                        append(device.sdk)
                        append(" · ")
                        append(device.supportedAbis.firstOrNull() ?: "ABI desconocida")
                        append(" · ")
                        append(device.densityDpi)
                        append(" dpi")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            TextButton(onClick = onReload) {
                Text("Recargar")
            }
        }
    }
}

@Composable
private fun InstalledAppCard(
    app: InstalledApp,
    selected: Boolean,
    selectionEnabled: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(selected, onCheckedChange = onSelectedChange, enabled = selectionEnabled)
                Text(
                    text = app.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = "${app.versionName ?: "Sin versión"} · código ${app.versionCode}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                StatusLabel(if (app.isSystemApp) "Sistema" else "Usuario")
                if (!app.isEnabled) {
                    StatusLabel("Deshabilitada")
                }
                if (app.splitSourceDirs.isNotEmpty()) {
                    StatusLabel("${app.splitSourceDirs.size} splits")
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
