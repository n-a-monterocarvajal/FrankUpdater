/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.gson.Gson
import io.github.n_a_monterocarvajal.frankupdater.MainActivity
import io.github.n_a_monterocarvajal.frankupdater.R
import io.github.n_a_monterocarvajal.frankupdater.compatibility.ReleaseChannel
import io.github.n_a_monterocarvajal.frankupdater.compatibility.VersionCatalog
import io.github.n_a_monterocarvajal.frankupdater.device.AndroidGenericDeviceProfileProvider
import io.github.n_a_monterocarvajal.frankupdater.inventory.AndroidInstalledAppRepository
import io.github.n_a_monterocarvajal.frankupdater.sources.MirrorAppStore
import io.github.n_a_monterocarvajal.frankupdater.sources.label
import io.github.n_a_monterocarvajal.frankupdater.sources.lookupSources
import io.github.n_a_monterocarvajal.frankupdater.sources.WebSourceClient
import io.github.n_a_monterocarvajal.frankupdater.sources.requirePackageName
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

internal data class UpdateObservation(val packageName: String, val installed: Long, val available: Long?, val status: String,
    val source: String? = null, val label: String? = null, val installedName: String? = null, val availableName: String? = null)

class UpdatePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("update_checks", 0)
    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) { preferences.edit().putBoolean("enabled", value).apply() }
    var packages: Set<String>
        get() = preferences.getStringSet("packages", emptySet()).orEmpty().toSet()
        set(value) {
            require(value.size <= 50) { "Selecciona como máximo 50 paquetes." }
            value.forEach(::requirePackageName)
            preferences.edit().putStringSet("packages", value.toSet()).apply()
        }
    internal fun save(rows: List<UpdateObservation>) {
        preferences.edit().putString("observations", Gson().toJson(rows))
            .putLong("checked_at", System.currentTimeMillis()).apply()
    }
    internal fun observations(): List<UpdateObservation> = runCatching {
        Gson().fromJson(preferences.getString("observations", "[]"), Array<UpdateObservation>::class.java).toList()
    }.getOrDefault(emptyList())
    val checkedAt: Long get() = preferences.getLong("checked_at", 0)
}

object UpdateSchedule {
    private const val PERIODIC = "periodic-update-check"
    private const val MANUAL = "manual-update-check"
    private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
    fun configure(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (UpdatePreferences(context).enabled) manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS).setConstraints(constraints()).build())
        else manager.cancelUniqueWork(PERIODIC)
    }
    fun checkNow(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<UpdateCheckWorker>().setInputData(workDataOf("manual" to true)).setConstraints(constraints()).build())
}

class UpdateCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val preferences = UpdatePreferences(applicationContext)
        if (!preferences.enabled && !inputData.getBoolean("manual", false)) return Result.success()
        return try {
            // ponytail: sequential, up to ~16 requests per package; parallelize per source if 50 packages exceed this.
            withTimeout(20 * 60 * 1000L) {
                val repository = AndroidInstalledAppRepository(applicationContext)
                val installed = repository.getInstalledApps().filter { it.packageName in preferences.packages }
                val device = AndroidGenericDeviceProfileProvider(applicationContext).getDeviceProfile()
                val client = WebSourceClient()
                val mirrorApps = MirrorAppStore(applicationContext)
                val rows = installed.map { app ->
                    ensureActive()
                    try {
                        val signers = repository.signers(app.packageName)
                        val lookup = runInterruptible(Dispatchers.IO) {
                            lookupSources(client, app.packageName, device.sdk, signers, mirrorApps[app.packageName])
                        }
                        mirrorApps.remember(app.packageName, lookup)
                        lookup.failures.forEach { (source, reason) -> Log.w("FrankUpdater", "${app.packageName} @ $source: $reason") }
                        if (lookup.entries.isEmpty()) {
                            UpdateObservation(app.packageName, app.versionCode, null,
                                if (lookup.failedSources.isEmpty()) "No figura en APKPure, APKMirror, F-Droid ni IzzyOnDroid"
                                else "Fuente no disponible", label = app.displayName, installedName = app.versionName)
                        } else {
                            val selection = VersionCatalog().select(app.packageName, lookup.entries, device, app.versionCode)
                            val eligible = selection.assessments.filter {
                                it.entry.artifact.versionCode > app.versionCode && it.incompatibilities.isEmpty() &&
                                    it.entry.channel != ReleaseChannel.Preview
                            }.map { it.entry.artifact }
                            // A source that publishes the installed signer beats a higher version whose signer is unknown:
                            // e.g. IzzyOnDroid's developer build cannot update an F-Droid-signed install.
                            val confirmed = eligible.firstOrNull { it.signerDigests.any(signers::contains) }
                            val best = confirmed ?: eligible.firstOrNull { it.signerDigests.isEmpty() }
                            UpdateObservation(app.packageName, app.versionCode, best?.versionCode,
                                when {
                                    confirmed != null -> "Versión por verificar · firma coincide con la instalada"
                                    best != null -> "Versión por verificar · firma sin confirmar hasta descargar"
                                    else -> "Sin versión superior elegible en el historial consultado"
                                },
                                best?.source?.label, app.displayName, app.versionName, best?.versionName)
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        UpdateObservation(app.packageName, app.versionCode, null, "Fuente no disponible",
                            label = app.displayName, installedName = app.versionName)
                    }
                }
                preferences.save(rows)
                val count = rows.count { it.available != null }
                if (count > 0) notifyResults(count)
            }
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.failure() }
    }

    private fun notifyResults(count: Int) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("updates", "Versiones disponibles", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, "updates").setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("$count aplicaciones con versiones por verificar")
            .setContentText("Revisa la compatibilidad antes de descargar e instalar.")
            .setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(applicationContext).notify(7, notification) }
        catch (_: SecurityException) { /* Permission may have changed after the check. Results remain in the app. */ }
    }
}
