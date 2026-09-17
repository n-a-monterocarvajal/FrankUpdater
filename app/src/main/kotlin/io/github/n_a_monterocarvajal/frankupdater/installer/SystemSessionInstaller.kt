/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Session sizing, split writes, fsync and explicit result handling are adapted
 * from Aurora Store SessionInstaller.kt at
 * f1bb85ff9dcbcc5cae07779d4e13f77b6b7f245b (GPL-2.0-or-later).
 */
package io.github.n_a_monterocarvajal.frankupdater.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import io.github.n_a_monterocarvajal.frankupdater.verification.VerifiedPackageArchive
import java.io.OutputStream
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface InstallRequestResult {
    data class Committed(val sessionId: Int) : InstallRequestResult

    data class PermissionRequired(val settingsIntent: Intent) : InstallRequestResult
    data class Legacy(val intent: Intent) : InstallRequestResult
    data object Finished : InstallRequestResult
}

class SystemSessionInstaller(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val writer: PackageSessionWriter = PackageSessionWriter(),
) {
    private val appContext = context.applicationContext
    private val packageInstaller = appContext.packageManager.packageInstaller

    suspend fun install(packageArchive: VerifiedPackageArchive): InstallRequestResult =
        withContext(ioDispatcher) {
            permissionIntent()?.let { return@withContext InstallRequestResult.PermissionRequired(it) }
            val sessionParams = buildSessionParams(packageArchive)
            val sessionId = packageInstaller.createSession(sessionParams)
            val session = packageInstaller.openSession(sessionId)
            try {
                val sessionApks = packageArchive.apks.mapIndexed { index, apk ->
                    SessionApk(
                        name = sessionName(index, apk.entryName, apk.isBase),
                        file = apk.file,
                    )
                }
                writer.write(sessionApks, AndroidInstallSessionHandle(session))
                session.commit(callbackIntent(sessionId).intentSender)
                InstallRequestResult.Committed(sessionId)
            } catch (error: Exception) {
                runCatching { session.abandon() }
                throw error
            } finally {
                session.close()
            }
        }

    internal fun permissionIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            null
        }

    private fun buildSessionParams(packageArchive: VerifiedPackageArchive) =
        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageArchive.packageName)
            setSize(packageArchive.apks.sumOf { it.file.length() })
            setInstallLocation(PackageInfo.INSTALL_LOCATION_AUTO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setOriginatingUid(Process.myUid())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
            }
        }

    private fun callbackIntent(sessionId: Int): PendingIntent {
        val intent = Intent(appContext, InstallStatusReceiver::class.java).apply {
            action = InstallStatusReceiver.ACTION_INSTALL_STATUS
            setPackage(appContext.packageName)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
        }
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getBroadcast(
            appContext,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
    }

    private fun sessionName(index: Int, entryName: String, isBase: Boolean): String {
        if (isBase) return "base.apk"
        val original = entryName.substringAfterLast('/').substringAfterLast('\\')
        val stem = original.removeSuffix(".apk")
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
            .ifBlank { "split" }
        return String.format(Locale.ROOT, "%03d_%s.apk", index, stem)
    }

    private class AndroidInstallSessionHandle(
        private val session: PackageInstaller.Session,
    ) : InstallSessionHandle {
        override fun openWrite(name: String, lengthBytes: Long): OutputStream =
            session.openWrite(name, 0, lengthBytes)

        override fun fsync(output: OutputStream) {
            session.fsync(output)
        }
    }
}
