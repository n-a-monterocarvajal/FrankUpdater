package io.github.n_a_monterocarvajal.frankupdater.ui

import io.github.n_a_monterocarvajal.frankupdater.R

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.window.core.layout.WindowSizeClass
import io.github.n_a_monterocarvajal.frankupdater.device.GenericDeviceProfileProvider
import io.github.n_a_monterocarvajal.frankupdater.inventory.InstalledAppRepository
import io.github.n_a_monterocarvajal.frankupdater.installer.SystemSessionInstaller
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackageLibrary
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackagePipeline
import io.github.n_a_monterocarvajal.frankupdater.storage.RetentionPreferences

internal enum class NavigationDestination(
    val label: String,
    val navigationLabel: String,
    val compactLabel: String,
    @param:androidx.annotation.DrawableRes val icon: Int,
    val description: String,
) {
    Updates(
        label = "Actualizaciones",
        navigationLabel = "Actualizaciones",
        compactLabel = "Actualizar",
        icon = R.drawable.ic_nav_updates,
        description = "Versiones nuevas de las apps elegidas y su estado.",
    ),
    Apps(
        label = "Apps",
        navigationLabel = "Apps",
        compactLabel = "Apps",
        icon = R.drawable.ic_nav_apps,
        description = "Inventario del dispositivo y apps elegidas para comprobar.",
    ),
    Search(
        label = "Buscar",
        navigationLabel = "Buscar",
        compactLabel = "Buscar",
        icon = R.drawable.ic_nav_search,
        description = "La búsqueda multifuente se incorporará tras validar el núcleo local.",
    ),
    Library(
        label = "Biblioteca",
        navigationLabel = "Biblioteca",
        compactLabel = "Biblioteca",
        icon = R.drawable.ic_nav_library,
        description = "Los paquetes conservados estarán disponibles para reinstalar o compartir.",
    ),
    Settings(
        label = "Ajustes",
        navigationLabel = "Ajustes",
        compactLabel = "Ajustes",
        icon = R.drawable.ic_nav_settings,
        description = "Preferencias de fuentes, instalación y retención.",
    ),
}

@Composable
fun FrankUpdaterApp(
    installedAppRepository: InstalledAppRepository,
    deviceProfileProvider: GenericDeviceProfileProvider,
    packagePipeline: LocalPackagePipeline,
    packageLibrary: LocalPackageLibrary,
    retentionPreferences: RetentionPreferences,
    sessionInstaller: SystemSessionInstaller,
) {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val useNavigationRail = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    )
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val destinations = NavigationDestination.entries
    // Package handed from an update result to Search, consumed once Search starts the query.
    var requestedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val openPackage = { packageName: String ->
        requestedPackage = packageName
        selectedIndex = destinations.indexOf(NavigationDestination.Search)
    }
    val navigate = { destination: NavigationDestination -> selectedIndex = destinations.indexOf(destination) }

    Surface(modifier = Modifier.fillMaxSize()) {
        if (useNavigationRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
            ) {
                NavigationRail {
                    destinations.forEachIndexed { index, destination ->
                        NavigationRailItem(
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                            icon = { androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(destination.icon), contentDescription = null) },
                            label = {
                                Text(
                                    text = destination.compactLabel,
                                    modifier = Modifier.widthIn(max = 72.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
                DestinationContent(
                    destination = destinations[selectedIndex],
                    installedAppRepository = installedAppRepository,
                    deviceProfileProvider = deviceProfileProvider,
                    packagePipeline = packagePipeline,
                    packageLibrary = packageLibrary,
                    retentionPreferences = retentionPreferences,
                    sessionInstaller = sessionInstaller,
                    requestedPackage = requestedPackage,
                    onOpenPackage = openPackage,
                    onPackageConsumed = { requestedPackage = null },
                    onNavigate = navigate,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Scaffold(
                modifier = Modifier.safeDrawingPadding(),
                bottomBar = {
                    NavigationBar {
                        destinations.forEachIndexed { index, destination ->
                            NavigationBarItem(
                                selected = selectedIndex == index,
                                onClick = { selectedIndex = index },
                            icon = { androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(destination.icon), contentDescription = null) },
                                label = {
                                    Text(
                                        text = destination.compactLabel,
                                        modifier = Modifier.widthIn(max = 96.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                },
            ) { contentPadding ->
                DestinationContent(
                    destination = destinations[selectedIndex],
                    installedAppRepository = installedAppRepository,
                    deviceProfileProvider = deviceProfileProvider,
                    packagePipeline = packagePipeline,
                    packageLibrary = packageLibrary,
                    retentionPreferences = retentionPreferences,
                    sessionInstaller = sessionInstaller,
                    requestedPackage = requestedPackage,
                    onOpenPackage = openPackage,
                    onPackageConsumed = { requestedPackage = null },
                    onNavigate = navigate,
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
    }
}

@Composable
private fun DestinationContent(
    destination: NavigationDestination,
    installedAppRepository: InstalledAppRepository,
    deviceProfileProvider: GenericDeviceProfileProvider,
    packagePipeline: LocalPackagePipeline,
    packageLibrary: LocalPackageLibrary,
    retentionPreferences: RetentionPreferences,
    sessionInstaller: SystemSessionInstaller,
    requestedPackage: String?,
    onOpenPackage: (String) -> Unit,
    onPackageConsumed: () -> Unit,
    onNavigate: (NavigationDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "FrankUpdater",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        // Wide layouts (F-46): content keeps a readable width, centered, instead of stretching across the screen.
        androidx.compose.foundation.layout.Box(
            Modifier.weight(1f).fillMaxWidth()
                .wrapContentWidth(androidx.compose.ui.Alignment.CenterHorizontally).widthIn(max = 840.dp),
        ) {
        when (destination) {
            NavigationDestination.Updates -> UpdatesRoute(
                onOpenPackage = onOpenPackage,
                onChooseApps = { onNavigate(NavigationDestination.Apps) },
                modifier = Modifier.fillMaxSize(),
            )
            NavigationDestination.Apps -> AppsRoute(
                installedAppRepository,
                deviceProfileProvider,
                onChecking = { onNavigate(NavigationDestination.Updates) },
                modifier = Modifier.fillMaxSize(),
            )
            NavigationDestination.Library -> LibraryRoute(
                packagePipeline,
                packageLibrary,
                retentionPreferences,
                sessionInstaller,
                Modifier.fillMaxSize(),
            )
            NavigationDestination.Settings -> SettingsRoute(
                retentionPreferences,
                Modifier.fillMaxSize(),
            )
            NavigationDestination.Search -> PlayRoute(packagePipeline, packageLibrary, requestedPackage, onPackageConsumed)
        }
        }
    }
}
