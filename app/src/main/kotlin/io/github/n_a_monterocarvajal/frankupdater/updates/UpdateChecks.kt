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
import io.github.n_a_monterocarvajal.frankupdater.model.Source
import io.github.n_a_monterocarvajal.frankupdater.sources.PureParser
import io.github.n_a_monterocarvajal.frankupdater.sources.WebSourceClient
import io.github.n_a_monterocarvajal.frankupdater.sources.requirePackageName
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

internal data class UpdateObservation(val packageName: String, val installed: Long, val available: Long?, val status: String)

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
            withTimeout(8 * 60 * 1000L) {
                val installed = AndroidInstalledAppRepository(applicationContext).getInstalledApps()
                    .filter { it.packageName in preferences.packages }
                val device = AndroidGenericDeviceProfileProvider(applicationContext).getDeviceProfile()
                val client = WebSourceClient()
                val rows = installed.map { app ->
                    ensureActive()
                    try {
                        val entries = runInterruptible(Dispatchers.IO) {
                            PureParser.history(client.text("https://tapi.pureapk.com/v3/get_app_his_version?package_name=${app.packageName}&hl=en",
                                Source.ApkPure, mapOf("Ual-Access-Businessid" to "projecta", "Ual-Access-ProjectA" to
                                    "{\"device_info\":{\"os_ver\":\"${device.sdk}\"}}")), app.packageName)
                        }
                        val selection = VersionCatalog().select(app.packageName, entries, device, app.versionCode)
                        val available = selection.assessments.firstOrNull {
                            it.entry.artifact.versionCode > app.versionCode && it.incompatibilities.isEmpty() &&
                                it.entry.channel != ReleaseChannel.Preview
                        }?.entry?.artifact?.versionCode
                        UpdateObservation(app.packageName, app.versionCode, available,
                            if (available != null) "Versión por verificar" else "Sin versión superior elegible en el historial consultado")
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { UpdateObservation(app.packageName, app.versionCode, null, "Fuente no disponible") }
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
