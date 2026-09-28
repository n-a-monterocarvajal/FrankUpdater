/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.n_a_monterocarvajal.frankupdater.model.Source
import io.github.n_a_monterocarvajal.frankupdater.sources.label
import io.github.n_a_monterocarvajal.frankupdater.updates.PackageDownloads

/**
 * Where a background download of [versionCode] stands, drawn right under the button that started it:
 * one line per step, the active one with its progress, and on failure what to try next.
 * Shows nothing when the package's last run was for another version.
 */
@Composable
internal fun DownloadSteps(
    packageName: String,
    versionCode: Long,
    onRetry: (source: Source?, install: Boolean) -> Unit,
    alternatives: List<Source> = emptyList(),
) {
    val context = LocalContext.current
    val flow = remember(packageName) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(PackageDownloads.workName(packageName))
    }
    val infos by flow.collectAsState(emptyList())
    val info = infos.lastOrNull()?.takeIf { PackageDownloads.codeTag(versionCode) in it.tags } ?: return
    val source = Source.entries.firstOrNull { PackageDownloads.sourceTag(it) in info.tags }
    val install = PackageDownloads.INSTALL_TAG in info.tags
    val labels = listOfNotNull(
        if (source == Source.GooglePlay) "Pedir a Google Play" else "Buscar el archivo en ${source?.label ?: "la fuente"}",
        "Descargar", "Verificar paquete y firma", if (install) "Instalar" else "Guardar en Biblioteca",
    )
    val active = when (info.progress.getString("stage")) {
        "download" -> 1; "verify" -> 2; "install" -> 3; else -> 0
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Descarga desde ${source?.label ?: "la fuente"}", style = MaterialTheme.typography.titleSmall)
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    Step("…", "En cola: empieza en cuanto haya conexión.", StepState.Active)
                    TextButton(onClick = { PackageDownloads.cancel(context, packageName) }) { Text("Cancelar") }
                }
                WorkInfo.State.RUNNING -> {
                    labels.forEachIndexed { index, label ->
                        val state = when {
                            index < active -> StepState.Done
                            index == active -> StepState.Active
                            else -> StepState.Pending
                        }
                        Step("${index + 1}", label, state)
                        if (index == 1 && state == StepState.Active) {
                            val bytes = info.progress.getLong("bytes", 0)
                            val total = info.progress.getLong("total", -1)
                            Column(Modifier.padding(start = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (total > 0) LinearProgressIndicator({ (bytes.toFloat() / total).coerceIn(0f, 1f) },
                                    Modifier.fillMaxWidth())
                                else LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(Formatter.formatShortFileSize(context, bytes) +
                                    (if (total > 0) " de " + Formatter.formatShortFileSize(context, total) else ""),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Text("Sigue aunque cierres la app o cambies de pestaña.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { PackageDownloads.cancel(context, packageName) }) { Text("Cancelar") }
                }
                WorkInfo.State.SUCCEEDED -> Step("✓", info.outputData.getString("message") ?: "Completado.", StepState.Done)
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    Step("✕", if (info.state == WorkInfo.State.CANCELLED) "Descarga cancelada."
                        else "No se pudo completar: ${info.outputData.getString("message") ?: "la fuente no respondió."}",
                        StepState.Failed)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        alternatives.firstOrNull { it != source }?.let { next ->
                            OutlinedButton(onClick = { onRetry(next, install) }) { Text("Probar con ${next.label}") }
                        }
                        TextButton(onClick = { onRetry(source, install) }) { Text("Reintentar") }
                    }
                }
            }
        }
    }
}

private enum class StepState { Done, Active, Pending, Failed }

@Composable
private fun Step(mark: String, label: String, state: StepState) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (state) {
                StepState.Active -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                StepState.Done -> Text("✓", color = colors.primary)
                StepState.Failed -> Text("✕", color = colors.error)
                StepState.Pending -> Text(mark, color = colors.outline, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(label, color = when (state) {
            StepState.Pending -> colors.outline
            StepState.Failed -> colors.error
            else -> colors.onSurface
        }, style = MaterialTheme.typography.bodyMedium)
    }
}
