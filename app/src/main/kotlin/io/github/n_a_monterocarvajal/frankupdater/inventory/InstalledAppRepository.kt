/*
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Adapted from APKUpdater's AppsRepository and PackageInfo transformation at
 * 69b6fcdf52a7735ae17101efe1a0cd26222fb276.
 */
package io.github.n_a_monterocarvajal.frankupdater.inventory

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.github.n_a_monterocarvajal.frankupdater.model.InstalledApp
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface InstalledAppRepository {
    suspend fun getInstalledApps(): List<InstalledApp>
}

class AndroidInstalledAppRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : InstalledAppRepository {
    private val packageManager = context.applicationContext.packageManager

    override suspend fun getInstalledApps(): List<InstalledApp> = withContext(ioDispatcher) {
        installedPackages()
            .map(::toInstalledApp)
            .sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { app: InstalledApp -> app.displayName }
                    .thenBy { it.packageName },
            )
    }

    @Suppress("DEPRECATION")
    private fun installedPackages(): List<PackageInfo> {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        } else {
            PackageManager.GET_SIGNATURES.toLong()
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags))
        } else {
            packageManager.getInstalledPackages(flags.toInt())
        }
    }

    /**
     * SHA-256 and SHA-1 of the installed signing certificates (with rotation history), or empty when not installed.
     * Sources publish either digest (APKMirror SHA-256, APKPure SHA-1); the lengths never collide.
     */
    @Suppress("DEPRECATION")
    fun signers(packageName: String): Set<String> = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        signingCertificates(packageManager.getPackageInfo(packageName, flags)).flatMap { listOf(it.sha256Hex(), it.sha1Hex()) }.toSet()
    }.getOrDefault(emptySet())

    /** Installed versionCode, or null when the package is not installed. */
    @Suppress("DEPRECATION")
    fun versionCode(packageName: String): Long? = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun toInstalledApp(packageInfo: PackageInfo): InstalledApp {
        val applicationInfo = packageInfo.applicationInfo
        return InstalledApp(
            displayName = applicationInfo
                ?.runCatching { loadLabel(packageManager).toString() }
                ?.getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: packageInfo.packageName,
            packageName = packageInfo.packageName,
            versionName = packageInfo.versionName,
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            },
            signingCertificateHistory = signingCertificates(packageInfo)
                .map(ByteArray::sha256Hex),
            splitSourceDirs = applicationInfo?.splitSourceDirs?.toList().orEmpty(),
            installerSource = installerSource(packageInfo.packageName),
            isSystemApp = applicationInfo?.isSystemApplication() == true,
            isEnabled = applicationInfo?.enabled == true,
        )
    }

    @Suppress("DEPRECATION")
    private fun signingCertificates(packageInfo: PackageInfo): List<ByteArray> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.let { signingInfo ->
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            }
        } else {
            packageInfo.signatures
        }
        return signatures?.map { it.toByteArray() }.orEmpty()
    }

    @Suppress("DEPRECATION")
    private fun installerSource(packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(packageName).let { sourceInfo ->
                sourceInfo.installingPackageName ?: sourceInfo.initiatingPackageName
            }
        } else {
            packageManager.getInstallerPackageName(packageName)
        }
    }.getOrNull()
}

private fun ApplicationInfo.isSystemApplication(): Boolean =
    flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

internal fun ByteArray.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun ByteArray.sha1Hex(): String =
    MessageDigest.getInstance("SHA-1").digest(this).joinToString(separator = "") { byte -> "%02x".format(byte) }
