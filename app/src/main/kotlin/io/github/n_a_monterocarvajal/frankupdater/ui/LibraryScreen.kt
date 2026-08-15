package io.github.n_a_monterocarvajal.frankupdater.ui

import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import io.github.n_a_monterocarvajal.frankupdater.installer.InstallRequestResult
import io.github.n_a_monterocarvajal.frankupdater.installer.InstallationEvent
import io.github.n_a_monterocarvajal.frankupdater.installer.InstallationEvents
import io.github.n_a_monterocarvajal.frankupdater.installer.SystemSessionInstaller
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackageLibrary
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackagePipeline
import io.github.n_a_monterocarvajal.frankupdater.storage.PackageLibraryEntry
import io.github.n_a_monterocarvajal.frankupdater.storage.PackageRetentionPolicy
import io.github.n_a_monterocarvajal.frankupdater.storage.PreparedPackageImport
import io.github.n_a_monterocarvajal.frankupdater.storage.RetentionPreferences
import io.github.n_a_monterocarvajal.frankupdater.verification.InstallAction
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val packageMimeTypes = arrayOf(
    "application/vnd.android.package-archive",
    "application/x-apks",
    "application/vnd.apkm",
    "application/xapk-package-archive",
    "application/zip",
    "application/octet-stream",
)

@Composable
internal fun LibraryRoute(
    pipeline: LocalPackagePipeline,
    library: LocalPackageLibrary,
    retentionPreferences: RetentionPreferences,
    sessionInstaller: SystemSessionInstaller,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reloadToken by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<PreparedPackageImport?>(null) }
    var selectedRetained by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeSessionId by remember { mutableStateOf<Int?>(null) }
    var installing by remember { mutableStateOf(false) }
    var installMessage by remember { mutableStateOf<String?>(null) }
    var retentionDecisionNeeded by remember { mutableStateOf(false) }
    val latestSelected by rememberUpdatedState(selected)
    val entries by produceState(initialValue = emptyList<PackageLibraryEntry>(), reloadToken) {
        value = withContext(Dispatchers.IO) { library.list() }
    }

    suspend fun handleInstallationEvent(event: InstallationEvent) {
        if (event.sessionId != activeSessionId) return
        when (event) {
            is InstallationEvent.AwaitingConfirmation -> {
                installMessage = "Confirma la instalación en Android."
            }
            is InstallationEvent.Failure -> {
                installing = false
                installMessage = "Instalación fallida: ${event.message}"
            }
            is InstallationEvent.Success -> {
                installing = false
                installMessage = "Aplicación instalada correctamente."
                val prepared = selected
                if (prepared != null && !selectedRetained) {
                    when (retentionPreferences.policy) {
                        PackageRetentionPolicy.DeleteAutomatically -> {
                            prepared.close()
                            selected = null
                        }
                        PackageRetentionPolicy.KeepAlways -> {
                            runCatching { withContext(Dispatchers.IO) { library.retain(prepared) } }
                                .onSuccess {
                                    selectedRetained = true
                                    reloadToken += 1
                                }
                                .onFailure {
                                    installMessage = "Instalada, pero no se pudo conservar: ${it.message}"
                                }
                        }
                        PackageRetentionPolicy.Ask -> retentionDecisionNeeded = true
                    }
                }
            }
        }
    }

    fun import(uri: Uri) {
        scope.launch {
            importing = true
            errorMessage = null
            try {
                selected?.close()
                selected = pipeline.import(uri)
                selectedRetained = false
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                errorMessage = error.message ?: "No se pudo importar el paquete."
            } finally {
                importing = false
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        uri -> if (uri != null) import(uri)
    }
    val permissionSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        installMessage = "Permiso revisado. Pulsa instalar de nuevo para continuar."
    }

    LaunchedEffect(Unit) {
        InstallationEvents.events.collect { event ->
            handleInstallationEvent(event)
        }
    }

    DisposableEffect(Unit) {
        onDispose { latestSelected?.close() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Biblioteca local", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Importa APK, APKS, APKM o XAPK mediante el selector seguro de Android.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = { documentPicker.launch(packageMimeTypes) },
                enabled = !importing,
            ) {
                Text("Importar")
            }
        }

        if (importing) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text("Copiando y verificando el paquete…")
            }
        }
        errorMessage?.let { message ->
            Card(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Importación rechazada", fontWeight = FontWeight.SemiBold)
                    Text(message, modifier = Modifier.padding(top = 4.dp))
                    TextButton(onClick = { errorMessage = null }) { Text("Cerrar") }
                }
            }
        }
        selected?.let { prepared ->
            VerifiedImportCard(
                prepared = prepared,
                retained = selectedRetained,
                installing = installing,
                installMessage = installMessage,
                retentionDecisionNeeded = retentionDecisionNeeded,
                onInstall = {
                    scope.launch {
                        installing = true
                        installMessage = "Preparando la sesión de instalación…"
                        try {
                            when (val result = sessionInstaller.install(prepared.verified)) {
                                is InstallRequestResult.Committed -> {
                                    activeSessionId = result.sessionId
                                    installMessage = "Sesión enviada a Android."
                                    InstallationEvents.latest(result.sessionId)
                                        ?.let { handleInstallationEvent(it) }
                                }
                                is InstallRequestResult.PermissionRequired -> {
                                    installing = false
                                    installMessage = "Autoriza a FrankUpdater para instalar paquetes."
                                    permissionSettings.launch(result.settingsIntent)
                                }
                            }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            installing = false
                            installMessage = "No se pudo crear la sesión: ${error.message}"
                        }
                    }
                },
                onRetain = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { library.retain(prepared) } }
                            .onSuccess {
                                selectedRetained = true
                                retentionDecisionNeeded = false
                                reloadToken += 1
                            }
                            .onFailure { errorMessage = it.message }
                    }
                },
                onDiscard = {
                    prepared.close()
                    selected = null
                    selectedRetained = false
                    retentionDecisionNeeded = false
                    installMessage = null
                },
            )
        }

        if (entries.isEmpty() && selected == null && !importing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Todavía no hay paquetes conservados.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = PackageLibraryEntry::id) { entry ->
                    RetainedPackageCard(
                        entry = entry,
                        onReinstall = {
                            scope.launch {
                                importing = true
                                errorMessage = null
                                try {
                                    selected?.close()
                                    selected = pipeline.prepare(entry)
                                    selectedRetained = true
                                    retentionDecisionNeeded = false
                                    installMessage = null
                                } catch (error: Exception) {
                                    errorMessage = error.message
                                } finally {
                                    importing = false
                                }
                            }
                        },
                        onShare = { shareEntry(context, entry) },
                        onDelete = {
                            scope.launch {
                                val deleted = withContext(Dispatchers.IO) { library.delete(entry) }
                                if (deleted) reloadToken += 1
                                else errorMessage = "No se pudo eliminar el paquete conservado."
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VerifiedImportCard(
    prepared: PreparedPackageImport,
    retained: Boolean,
    installing: Boolean,
    installMessage: String?,
    retentionDecisionNeeded: Boolean,
    onInstall: () -> Unit,
    onRetain: () -> Unit,
    onDiscard: () -> Unit,
) {
    val packageArchive = prepared.verified
    Card(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Paquete verificado", color = MaterialTheme.colorScheme.primary)
            Text(
                packageArchive.packageName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${packageArchive.versionName ?: "Sin versión"} · código ${packageArchive.versionCode}",
            )
            Text(
                "${packageArchive.format.name} · ${packageArchive.apks.size} APK · " +
                    formatBytes(packageArchive.sourceSizeBytes),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "SHA-256 ${packageArchive.sourceSha256.take(16)}… · " +
                    packageArchive.installAction.name,
                style = MaterialTheme.typography.bodySmall,
            )
            if (packageArchive.expansionFiles.isNotEmpty()) {
                Text(
                    "Incluye ${packageArchive.expansionFiles.size} archivo(s) OBB; se conservan, " +
                        "pero esta etapa no los despliega.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Button(onClick = onInstall, enabled = !installing) {
                    Text(
                        when (packageArchive.installAction) {
                            InstallAction.Install -> "Instalar"
                            InstallAction.Update -> "Actualizar"
                            InstallAction.Reinstall -> "Reinstalar"
                        },
                    )
                }
                Button(onClick = onRetain, enabled = !retained) {
                    Text(if (retained) "Conservado" else "Conservar")
                }
                OutlinedButton(onClick = onDiscard) { Text("Descartar") }
            }
            installMessage?.let {
                Text(it, modifier = Modifier.padding(top = 10.dp))
            }
            if (retentionDecisionNeeded) {
                Text(
                    "¿Quieres conservar el paquete para usarlo más adelante?",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun RetainedPackageCard(
    entry: PackageLibraryEntry,
    onReinstall: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                entry.packageName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${entry.versionName ?: "Sin versión"} · código ${entry.versionCode}")
            Text(
                "${entry.format.name} · ${entry.apkCount} APK · ${formatBytes(entry.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Button(onClick = onReinstall) { Text("Reinstalar") }
                OutlinedButton(onClick = onShare) { Text("Compartir") }
                TextButton(onClick = onDelete) { Text("Eliminar") }
            }
        }
    }
}

private fun shareEntry(context: android.content.Context, entry: PackageLibraryEntry) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.files",
        entry.archiveFile,
    )
    val mimeType = if (entry.format.name == "APK") {
        "application/vnd.android.package-archive"
    } else {
        "application/octet-stream"
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir paquete"))
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
