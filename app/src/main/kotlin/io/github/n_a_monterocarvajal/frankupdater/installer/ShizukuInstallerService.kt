/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.installer

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor

/** Started exclusively through Shizuku's UserService API; not an exported Android Service. */
class ShizukuInstallerService(context: Context) : IInstallerService.Stub() {
    private val ownerUid = context.packageManager.getApplicationInfo(context.packageName, 0).uid

    override fun execute(arguments: Array<out String>, input: ParcelFileDescriptor?): Bundle {
        check(Binder.getCallingUid() == ownerUid) { "Unauthorized client" }
        require(arguments.size in 3..12 && arguments[0] == "/system/bin/pm" &&
            arguments[1] in listOf("install-create", "install-write", "install-commit", "install-abandon") &&
            arguments.drop(2).all { Regex("[A-Za-z0-9_.-]+").matches(it) })
        val stream = input?.let { ParcelFileDescriptor.AutoCloseInputStream(it) }
        val result = stream.use { runInstallerProcess(ProcessBuilder(arguments.toList()).start(), it) }
        return Bundle().apply { putInt("code", result.code); putString("output", result.output) }
    }

    override fun destroy() { kotlin.system.exitProcess(0) }
}
