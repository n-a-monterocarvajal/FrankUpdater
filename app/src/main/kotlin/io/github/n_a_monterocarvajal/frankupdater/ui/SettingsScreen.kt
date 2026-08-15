package io.github.n_a_monterocarvajal.frankupdater.ui

import androidx.compose.foundation.clickable
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

@Composable
internal fun SettingsRoute(
    retentionPreferences: RetentionPreferences,
    modifier: Modifier = Modifier,
) {
    var policy by remember { mutableStateOf(retentionPreferences.policy) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text("Retención tras instalar", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Los paquetes se guardan sólo en el espacio privado de FrankUpdater.",
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
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
