/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.updates

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import io.github.n_a_monterocarvajal.frankupdater.MainActivity
import io.github.n_a_monterocarvajal.frankupdater.R
import io.github.n_a_monterocarvajal.frankupdater.compatibility.ReleaseChannel
import io.github.n_a_monterocarvajal.frankupdater.installer.InstallRequestResult
import io.github.n_a_monterocarvajal.frankupdater.installer.InstallationEvent
import io.github.n_a_monterocarvajal.frankupdater.installer.InstallationEvents
import io.github.n_a_monterocarvajal.frankupdater.installer.InstallerRouter
import io.github.n_a_monterocarvajal.frankupdater.model.PackageType
import io.github.n_a_monterocarvajal.frankupdater.model.Source
import io.github.n_a_monterocarvajal.frankupdater.sources.MirrorParser
import io.github.n_a_monterocarvajal.frankupdater.sources.WebSourceClient
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackageLibrary
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackagePipeline
import io.github.n_a_monterocarvajal.frankupdater.storage.PackageRetentionPolicy
import io.github.n_a_monterocarvajal.frankupdater.storage.PreparedPackageImport
import io.github.n_a_monterocarvajal.frankupdater.storage.RetentionPreferences
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** One package version to fetch from a web source. `pageUrl` is resolved at download time unless `directUrl` is set. */
internal data class DownloadRequest(
    val packageName: String, val versionCode: Long, val source: Source, val type: PackageType, val pageUrl: String,
    val directUrl: String? = null, val sha256: String? = null, val sizeBytes: Long? = null,
    val channel: ReleaseChannel = ReleaseChannel.Unknown, val install: Boolean = false,
) {
    fun toData(): Data = workDataOf("package" to packageName, "code" to versionCode, "source" to source.name,
        "type" to type.name, "page" to pageUrl, "direct" to directUrl, "sha256" to sha256, "size" to (sizeBytes ?: -1L),
        "channel" to channel.name, "install" to install)

    companion object {
        fun from(data: Data) = DownloadRequest(requireNotNull(data.getString("package")), data.getLong("code", -1),
            Source.valueOf(requireNotNull(data.getString("source"))), PackageType.valueOf(requireNotNull(data.getString("type"))),
            requireNotNull(data.getString("page")), data.getString("direct"), data.getString("sha256"),
            data.getLong("size", -1).takeIf { it > 0 }, ReleaseChannel.valueOf(data.getString("channel") ?: "Unknown"),
            data.getBoolean("install", false))
    }
}

/**
 * Downloads, verifies and hands the prepared package to [block]; the downloaded file lives until [block] returns.
 * [capturedUrl] and [headers] come from the assisted web view and never go through WorkManager's database.
 */
internal suspend fun <T> withVerifiedDownload(context: Context, client: WebSourceClient, pipeline: LocalPackagePipeline,
    request: DownloadRequest, capturedUrl: String? = null, headers: Map<String, String> = emptyMap(),
    onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> }, onVerifying: () -> Unit = {},
    block: suspend (PreparedPackageImport) -> T): T {
    val directory = File(context.cacheDir, "web-download/${UUID.randomUUID()}")
    val extension = when (request.type) { PackageType.Apkm -> "apkm"; PackageType.Xapk -> "xapk"; else -> "apk" }
    try {
        val archive = runInterruptible(Dispatchers.IO) {
            val url = capturedUrl ?: request.directUrl ?: run {
                val page = MirrorParser.downloadPage(client.text(request.pageUrl, request.source), request.pageUrl)
                MirrorParser.downloadUrl(client.text(page, request.source), page)
            }
            var shownAt = 0L
            client.download(url, request.source, File(directory, "download.$extension"), headers, request.sha256,
                request.sizeBytes) { bytes, total ->
                // One update per 256 KiB is enough for a smooth bar.
                if (bytes - shownAt >= 262_144 || bytes == total) { shownAt = bytes; onProgress(bytes, total) }
            }
        }
        onVerifying()
        return pipeline.importDownloadedArchive(archive, "${request.packageName}-${request.versionCode}.$extension",
            request.packageName, request.versionCode, request.pageUrl,
            if (capturedUrl == null) "direct-${request.source.name}" else "assisted-web").use { block(it) }
    } finally {
        withContext(Dispatchers.IO + NonCancellable) { directory.deleteRecursively() }
    }
}

object PackageDownloads {
    internal fun workName(packageName: String) = "download-$packageName"

    internal fun enqueue(context: Context, request: DownloadRequest) {
        WorkManager.getInstance(context).enqueueUniqueWork(workName(request.packageName), ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PackageDownloadWorker>().setInputData(request.toData())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build())
    }

    internal fun cancel(context: Context, packageName: String) =
        WorkManager.getInstance(context).cancelUniqueWork(workName(packageName))
}

/**
 * Foreground download (survives the app being closed). With `install`, the verified package is installed right
 * away: silently when Android allows it (Shizuku/root, or FrankUpdater as installer of record on Android 12+),
 * otherwise through a notification. Without `install`, it is kept in the library as before.
 */
class PackageDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val notifications = applicationContext.getSystemService(NotificationManager::class.java)

    override suspend fun doWork(): Result {
        val request = DownloadRequest.from(inputData)
        val id = request.packageName.hashCode()
        return try {
            setForeground(foreground(id, request.packageName, 0, -1))
            val message = withVerifiedDownload(applicationContext, WebSourceClient(), LocalPackagePipeline(applicationContext),
                request, onProgress = { bytes, total ->
                    setProgressAsync(workDataOf("bytes" to bytes, "total" to total))
                    notifications.notify(id, progressNotification(request.packageName, bytes, total))
                }, onVerifying = {
                    setProgressAsync(workDataOf("verifying" to true))
                }) { prepared ->
                if (request.install) install(prepared, request) else {
                    runInterruptible(Dispatchers.IO) { LocalPackageLibrary(applicationContext).retain(prepared) }
                    "Paquete verificado y guardado en Biblioteca."
                }
            }
            Result.success(workDataOf("message" to message))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            Log.w("FrankUpdater", "Background download failed for ${request.packageName}", error)
            if (request.install) notify(id + 1, "No se pudo actualizar ${request.packageName}",
                error.message ?: error.javaClass.simpleName, null)
            Result.failure(workDataOf("message" to (error.message ?: error.javaClass.simpleName)))
        }
    }

    private suspend fun install(prepared: PreparedPackageImport, request: DownloadRequest): String {
        val keep = RetentionPreferences(applicationContext).policy == PackageRetentionPolicy.KeepAlways
        if (keep) runInterruptible(Dispatchers.IO) { LocalPackageLibrary(applicationContext).retain(prepared) }
        val name = prepared.verified.versionName ?: request.versionCode.toString()
        return when (val result = InstallerRouter(applicationContext).install(prepared.verified, background = true)) {
            InstallRequestResult.Finished -> done(request, name)
            is InstallRequestResult.Committed -> {
                // The session already holds the APKs, so the prepared files may go once we know the outcome.
                fun settled(event: InstallationEvent?) = event?.takeIf { it !is InstallationEvent.AwaitingConfirmation }
                val outcome = settled(InstallationEvents.latest(result.sessionId)) ?: withTimeoutOrNull(2 * 60_000L) {
                    InstallationEvents.events.first { it.sessionId == result.sessionId && settled(it) != null }
                } ?: InstallationEvents.latest(result.sessionId)
                when (outcome) {
                    is InstallationEvent.Success -> done(request, name)
                    is InstallationEvent.Failure -> throw IllegalStateException(outcome.message)
                    else -> "Esperando confirmación de instalación."
                }
            }
            is InstallRequestResult.PermissionRequired -> {
                notify(request.packageName.hashCode() + 1, "Autoriza a FrankUpdater para instalar",
                    "Necesario para actualizar ${request.packageName} automáticamente.", result.settingsIntent)
                throw IllegalStateException("Falta el permiso para instalar aplicaciones.")
            }
            is InstallRequestResult.Legacy -> {
                notify(request.packageName.hashCode() + 1, "Toca para instalar ${request.packageName} $name",
                    "El modo Legacy requiere confirmar cada instalación.", result.intent)
                "Esperando confirmación de instalación."
            }
        }
    }

    private fun done(request: DownloadRequest, name: String): String {
        notify(request.packageName.hashCode() + 1, "${request.packageName} actualizada a $name", "Paquete verificado e instalado.", null)
        return "Instalada $name."
    }

    private fun foreground(id: Int, packageName: String, bytes: Long, total: Long): ForegroundInfo {
        val notification = progressNotification(packageName, bytes, total)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id, notification)
    }

    private fun progressNotification(packageName: String, bytes: Long, total: Long): Notification {
        channel("downloads", "Descargas", NotificationManager.IMPORTANCE_LOW)
        return NotificationCompat.Builder(applicationContext, "downloads").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Descargando $packageName").setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(if (total > 0) 100 else 0, if (total > 0) (bytes * 100 / total).toInt() else 0, total <= 0)
            .addAction(0, "Cancelar", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
    }

    private fun notify(id: Int, title: String, text: String, intent: Intent?) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        channel("installs", "Instalaciones", NotificationManager.IMPORTANCE_DEFAULT)
        val target = intent ?: Intent(applicationContext, MainActivity::class.java)
        val pending = PendingIntent.getActivity(applicationContext, id, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, "installs").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title).setContentText(text).setContentIntent(pending).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(applicationContext).notify(id, notification) }
        catch (_: SecurityException) { /* Notification permission revoked meanwhile; the result is still logged. */ }
    }

    private fun channel(id: String, name: String, importance: Int) {
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(NotificationChannel(id, name, importance))
    }
}
