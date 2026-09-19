package io.github.n_a_monterocarvajal.frankupdater.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.n_a_monterocarvajal.frankupdater.storage.PackageRetentionPolicy
import io.github.n_a_monterocarvajal.frankupdater.storage.RetentionPreferences
import io.github.n_a_monterocarvajal.frankupdater.installer.InstallerMode
import io.github.n_a_monterocarvajal.frankupdater.installer.InstallerPreferences
import io.github.n_a_monterocarvajal.frankupdater.updates.UpdatePreferences
import io.github.n_a_monterocarvajal.frankupdater.updates.UpdateSchedule
import rikka.shizuku.Shizuku

@Composable
internal fun SettingsRoute(
    retentionPreferences: RetentionPreferences,
    modifier: Modifier = Modifier,
) {
    var policy by remember { mutableStateOf(retentionPreferences.policy) }
    val context = LocalContext.current
    val installer = remember { InstallerPreferences(context) }
    val updates = remember { UpdatePreferences(context) }
    var mode by remember { mutableStateOf(installer.mode) }
    var enabled by remember { mutableStateOf(updates.enabled) }
    var message by remember { mutableStateOf("") }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        UiSection(
            title = "Retención tras instalar",
            supporting = "Los paquetes se guardan solo en el espacio privado de FrankUpdater.",
        ) {
            RetentionOption(
                title = "Preguntar",
                description = "Elegir entre eliminar o conservar después de cada instalación.",
                selected = policy == PackageRetentionPolicy.Ask,
                onClick = {
                    policy = PackageRetentionPolicy.Ask
                    retentionPreferences.policy = policy
                },
            )
            RetentionOption(
                title = "Eliminar automáticamente",
                description = "Borrar el archivo importado cuando la instalación termine correctamente.",
                selected = policy == PackageRetentionPolicy.DeleteAutomatically,
                onClick = {
                    policy = PackageRetentionPolicy.DeleteAutomatically
                    retentionPreferences.policy = policy
                },
            )
            RetentionOption(
                title = "Conservar siempre",
                description = "Añadir cada paquete verificado a la biblioteca local.",
                selected = policy == PackageRetentionPolicy.KeepAlways,
                onClick = {
                    policy = PackageRetentionPolicy.KeepAlways
                    retentionPreferences.policy = policy
                },
            )
        }
        UiSection(
            title = "Método de instalación",
            supporting = "Automático usa Shizuku si ya tiene permiso; en otro caso usa Sistema. Root solo se solicita al elegirlo.",
            modifier = Modifier.padding(top = 24.dp),
        ) {
            InstallerMode.entries.forEach { option ->
                RetentionOption(option.label, installerDescription(option), mode == option) {
                    mode = option
                    installer.mode = option
                }
            }
            Button(onClick = {
                message = try {
                    check(Shizuku.pingBinder())
                    if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) "Shizuku autorizado."
                    else { Shizuku.requestPermission(7); "Confirma el permiso en Shizuku y vuelve a instalar." }
                } catch (_: Exception) { "Inicia Shizuku antes de solicitar acceso." }
            }) { Text("Autorizar Shizuku") }
        }
        UiSection(
            title = "Comprobaciones periódicas",
            supporting = "Cada 24 horas, con red y batería suficiente. Se consultan APKPure, APKMirror, F-Droid e IzzyOnDroid " +
                "para las apps elegidas en Actualizaciones. Solo se descargan e instalan solas las que tengan activada " +
                "la actualización automática.",
            modifier = Modifier.padding(top = 24.dp),
        ) {
            // The selection lives in the Updates tab (single source of truth); here only its summary.
            Text("${updates.packages.size} apps elegidas en Actualizaciones · ${updates.autoUpdate.size} con actualización automática",
                style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(enabled, onCheckedChange = {
                    enabled = it; updates.enabled = it; UpdateSchedule.configure(context)
                    if (it && android.os.Build.VERSION.SDK_INT >= 33) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                })
                Text("Comprobar cada 24 horas")
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun installerDescription(mode: InstallerMode) = when (mode) {
    InstallerMode.System -> "Usa el instalador de Android y admite APK con splits."
    InstallerMode.Automatic -> "Prefiere Shizuku autorizado; si no, usa el instalador del sistema."
    InstallerMode.Shizuku -> "Instala mediante una sesión privilegiada de Shizuku."
    InstallerMode.Root -> "Instala mediante una sesión privilegiada con Root."
    InstallerMode.Legacy -> "Solo APK único; no admite splits ni actualizaciones por lote."
}

@Composable
private fun RetentionOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
