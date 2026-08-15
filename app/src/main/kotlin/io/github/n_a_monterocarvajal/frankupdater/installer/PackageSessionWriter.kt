/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.n_a_monterocarvajal.frankupdater.installer

import java.io.File
import java.io.OutputStream

data class SessionApk(
    val name: String,
    val file: File,
)

interface InstallSessionHandle {
    fun openWrite(name: String, lengthBytes: Long): OutputStream

    fun fsync(output: OutputStream)
}

class PackageSessionWriter {
    fun write(apks: List<SessionApk>, session: InstallSessionHandle) {
        require(apks.isNotEmpty())
        apks.forEach { apk ->
            apk.file.inputStream().use { input ->
                session.openWrite(apk.name, apk.file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
        }
    }
}
