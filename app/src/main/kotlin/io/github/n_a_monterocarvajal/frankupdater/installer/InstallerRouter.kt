/* SPDX-License-Identifier: GPL-3.0-only
 * Mode selection references Droid-ify InstallManager at ff1453ed957f3b2382abce9a7eb9a9db8def3a3a.
 */
package io.github.n_a_monterocarvajal.frankupdater.installer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import io.github.n_a_monterocarvajal.frankupdater.verification.VerifiedPackageArchive
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

enum class InstallerMode(val label: String) { System("Sistema"), Automatic("Automático"), Shizuku("Shizuku"), Root("Root"), Legacy("Legacy (APK único)") }

class InstallerPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("installer", 0)
    var mode: InstallerMode
        get() = runCatching { InstallerMode.valueOf(preferences.getString("mode", "System")!!) }.getOrDefault(InstallerMode.System)
        set(value) { preferences.edit().putString("mode", value.name).apply() }
}

internal fun selectedInstaller(mode: InstallerMode, shizukuGranted: Boolean): InstallerMode =
    if (mode == InstallerMode.Automatic) {
        if (shizukuGranted) InstallerMode.Shizuku else InstallerMode.System
    } else mode

class InstallerRouter(context: Context, private val system: SystemSessionInstaller = SystemSessionInstaller(context)) {
    private val context = context.applicationContext
    val preferences = InstallerPreferences(context)

    fun shizukuGranted(): Boolean = runCatching {
        Shizuku.pingBinder() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    suspend fun install(archive: VerifiedPackageArchive): InstallRequestResult = installLock.withLock {
        when (selectedInstaller(preferences.mode, shizukuGranted())) {
            InstallerMode.System, InstallerMode.Automatic -> system.install(archive)
            InstallerMode.Legacy -> {
                system.permissionIntent()?.let { return@withLock InstallRequestResult.PermissionRequired(it) }
                require(archive.apks.size == 1 && archive.apks.single().isBase) { "Legacy no admite splits. Selecciona Sistema." }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", archive.apks.single().file)
                @Suppress("DEPRECATION")
                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE, uri).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                }
                InstallRequestResult.Legacy(intent)
            }
            InstallerMode.Root -> runInterruptible(Dispatchers.IO) {
                val installer = ShellSessionInstaller { args, file ->
                    require(args.all { Regex("[A-Za-z0-9_./-]+").matches(it) })
                    file?.inputStream().use { runInstallerProcess(ProcessBuilder("su", "-c", args.joinToString(" ")).start(), it) }
                }
                installer.install(archive.apks.map { it.file }, context.packageName, android.os.Process.myUid() / 100000)
                InstallRequestResult.Finished
            }
            InstallerMode.Shizuku -> {
                check(shizukuGranted()) { "Inicia Shizuku y concede acceso en Ajustes, o selecciona Sistema." }
                installWithShizuku(archive)
                InstallRequestResult.Finished
            }
        }
    }

    private suspend fun installWithShizuku(archive: VerifiedPackageArchive) {
        val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuInstallerService::class.java))
            .daemon(false).processNameSuffix("installer").version(1)
        val connected = CompletableDeferred<IInstallerService>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { connected.complete(IInstallerService.Stub.asInterface(service)) }
            override fun onServiceDisconnected(name: ComponentName) { connected.cancel() }
        }
        try {
            withContext(Dispatchers.Main) { Shizuku.bindUserService(args, connection) }
            val service = withTimeout(15000) { connected.await() }
            runInterruptible(Dispatchers.IO) {
                ShellSessionInstaller { command, file ->
                    val result = file?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }.use {
                        service.execute(command.toTypedArray(), it)
                    }
                    ShellResult(result.getInt("code", -1), result.getString("output").orEmpty())
                }.install(archive.apks.map { it.file }, context.packageName, android.os.Process.myUid() / 100000)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { runCatching { Shizuku.unbindUserService(args, connection, true) } }
        }
    }

    private companion object {
        // ponytail: one installation at a time; introduce per-session queues only when parallel installs are required.
        val installLock = Mutex()
    }
}
