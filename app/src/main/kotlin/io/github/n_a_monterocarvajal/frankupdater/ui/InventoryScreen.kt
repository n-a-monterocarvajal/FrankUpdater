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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

@Composable
internal fun InventoryRoute(
    installedAppRepository: InstalledAppRepository,
    deviceProfileProvider: GenericDeviceProfileProvider,
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
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
        modifier = modifier.padding(24.dp),
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
    modifier: Modifier,
) {
    val systemCount = state.apps.count(InstalledApp::isSystemApp)
    val userCount = state.apps.size - systemCount

    Column(modifier = modifier) {
        DeviceSummary(
            device = state.device,
            appCount = state.apps.size,
            userCount = userCount,
            systemCount = systemCount,
            onReload = onReload,
        )
        if (state.apps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay aplicaciones visibles en este dispositivo.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = state.apps,
                    key = InstalledApp::packageName,
                ) { app ->
                    InstalledAppCard(app)
                }
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
                Text("Actualizar")
            }
        }
    }
}

@Composable
private fun InstalledAppCard(app: InstalledApp) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = app.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
