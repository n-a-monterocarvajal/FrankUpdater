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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
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

/** Process-wide copy of the last inventory read. */
private var lastInventory: InventoryUiState.Content? = null

/** Play Store and Amazon keep their apps updated themselves; Galaxy Store apps (Good Guardians) stay visible. */
internal fun fromPlayStore(app: InstalledApp) =
    app.installerSource?.let { it.contains("com.android.vending") || it.contains("com.amazon") } == true

internal fun filterInstalledApps(
    apps: List<InstalledApp>,
    query: String,
    filter: InventoryFilter,
    hideDisabled: Boolean = false,
    hidePlayStore: Boolean = false,
): List<InstalledApp> = apps.filter { app ->
    (filter == InventoryFilter.All || app.isSystemApp == (filter == InventoryFilter.System)) &&
        (!hideDisabled || app.isEnabled) && (!hidePlayStore || !fromPlayStore(app)) &&
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
    // The last inventory stays on screen while a fresh one loads, so revisiting the tab does not block on it.
    val state by produceState<InventoryUiState>(
        initialValue = lastInventory ?: InventoryUiState.Loading,
        key1 = installedAppRepository,
        key2 = deviceProfileProvider,
        key3 = reloadToken,
    ) {
        if (lastInventory == null) value = InventoryUiState.Loading
        value = try {
            withContext(Dispatchers.IO) {
                InventoryUiState.Content(
                    apps = installedAppRepository.getInstalledApps(),
                    device = deviceProfileProvider.getDeviceProfile(),
                ).also { lastInventory = it }
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
    val systemCount = remember(state.apps) { state.apps.count(InstalledApp::isSystemApp) }
    val userCount = state.apps.size - systemCount
    var query by rememberSaveable { mutableStateOf("") }
    // Filters are remembered across launches (F-50); the search text is not.
    val preferences = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("inventory", 0)
    var filter by remember { mutableStateOf(runCatching {
        InventoryFilter.valueOf(preferences.getString("filter", null) ?: "User") }.getOrDefault(InventoryFilter.User)) }
    var hideDisabled by remember { mutableStateOf(preferences.getBoolean("hide_disabled", true)) }
    var hidePlayStore by remember { mutableStateOf(preferences.getBoolean("hide_play_store", false)) }
    fun saveFilters() = preferences.edit().putString("filter", filter.name).putBoolean("hide_disabled", hideDisabled)
        .putBoolean("hide_play_store", hidePlayStore).apply()
    // Filter only when its inputs change, not on every recomposition (selection, scrolling).
    val visibleApps = remember(state.apps, query, filter, hideDisabled, hidePlayStore) {
        filterInstalledApps(state.apps, query, filter, hideDisabled, hidePlayStore)
    }
    val visibleSelection = selectedPackages.intersect(visibleApps.map(InstalledApp::packageName).toSet())
    val ownPackage = androidx.compose.ui.platform.LocalContext.current.packageName

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
                                onClick = { filter = option; saveFilters() },
                                label = { Text(when (option) {
                                    InventoryFilter.All -> "Todas"
                                    InventoryFilter.User -> "Usuario"
                                    InventoryFilter.System -> "Sistema"
                                }) },
                            )
                        }
                    }
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = hideDisabled, onClick = { hideDisabled = !hideDisabled; saveFilters() },
                            label = { Text("Ocultar desactivadas") })
                        FilterChip(selected = hidePlayStore, onClick = { hidePlayStore = !hidePlayStore; saveFilters() },
                            label = { Text("Ocultar de Play Store") })
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
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f, fill = false),
                    enabled = visibleApps.isNotEmpty() && selectedPackages.size < 50,
                    onClick = {
                        onSelectedPackagesChange(selectedPackages + visibleApps
                            .map(InstalledApp::packageName)
                            .filterNot { it in selectedPackages || it == ownPackage }
                            .take(50 - selectedPackages.size))
                    },
                ) { Text("Seleccionar ${visibleApps.count { it.packageName != ownPackage }}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
    // Compact list item: the whole row toggles selection; badges only when they add information.
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = selected, enabled = selectionEnabled, role = Role.Checkbox, onValueChange = onSelectedChange)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(selected, onCheckedChange = null, enabled = selectionEnabled)
            AppIcon(app.packageName, Modifier.padding(start = 4.dp).size(36.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = app.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${app.versionName ?: "Sin versión"} · ${app.packageName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (app.isSystemApp || !app.isEnabled || app.splitSourceDirs.isNotEmpty()) Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (app.isSystemApp) StatusLabel("Sistema")
                    if (!app.isEnabled) StatusLabel("Deshabilitada")
                    if (app.splitSourceDirs.isNotEmpty()) StatusLabel("${app.splitSourceDirs.size} splits")
                }
            }
        }
    }
}

/** Icon loaded off the main thread, only for rows the grid actually composes. */
// ponytail: 300 icons of 96×96 ≈ 11 MB at most; key by version too if stale icons after updates matter.
private val iconCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(300)

@Composable
private fun AppIcon(packageName: String, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Cached per process: rows that re-enter the list (search, filters, scrolling) do not decode icons again.
    val icon by androidx.compose.runtime.produceState(iconCache.get(packageName), packageName) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
                    .also { iconCache.put(packageName, it) }
            }.getOrNull()
        }
    }
    icon?.let { androidx.compose.foundation.Image(it, contentDescription = null, modifier = modifier) }
        ?: Box(modifier)
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
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
