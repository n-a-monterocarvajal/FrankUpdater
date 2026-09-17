/* SPDX-License-Identifier: GPL-3.0-only
 * Session command sequence adapted from Droid-ify ShizukuInstaller at
 * ff1453ed957f3b2382abce9a7eb9a9db8def3a3a. APKs are streamed, never shell paths.
 */
package io.github.n_a_monterocarvajal.frankupdater.installer

import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal data class ShellResult(val code: Int, val output: String)

internal class ShellSessionInstaller(private val execute: (List<String>, File?) -> ShellResult) {
    fun install(apks: List<File>, installerPackage: String, userId: Int): Int {
        require(apks.isNotEmpty() && apks.all { it.isFile && it.length() > 0 })
        require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(installerPackage))
        require(userId >= 0)
        val created = execute(listOf("/system/bin/pm", "install-create", "--user", "$userId", "-r", "-i", installerPackage,
            "-S", apks.sumOf { it.length() }.toString()), null)
        check(created.code == 0) { "No se pudo crear la sesión privilegiada." }
        val session = Regex("Success.*\\[(\\d+)]").find(created.output)?.groupValues?.get(1)?.toIntOrNull()
            ?: error("Android no devolvió un identificador de sesión válido.")
        var committed = false
        try {
            apks.forEachIndexed { index, file ->
                check(!Thread.currentThread().isInterrupted) { "Instalación cancelada." }
                val result = execute(listOf("/system/bin/pm", "install-write", "-S", "${file.length()}", "$session",
                    if (index == 0) "base.apk" else "split-$index.apk", "-"), file)
                check(result.code == 0 && result.output.trim().startsWith("Success")) { "Android rechazó un APK de la sesión." }
            }
            check(!Thread.currentThread().isInterrupted) { "Instalación cancelada." }
            val result = execute(listOf("/system/bin/pm", "install-commit", "$session"), null)
            check(result.code == 0 && result.output.trim() == "Success") { "Android no confirmó la instalación. No se reintentará automáticamente." }
            committed = true
            return session
        } finally {
            if (!committed) {
                val interrupted = Thread.interrupted()
                try { runCatching { execute(listOf("/system/bin/pm", "install-abandon", "$session"), null) } }
                finally { if (interrupted) Thread.currentThread().interrupt() }
            }
        }
    }
}

/** Bound output and wall time, drain both pipes concurrently, close stdin even for commands without APKs. */
internal fun runInstallerProcess(process: Process, input: InputStream?): ShellResult {
    val threads = Executors.newScheduledThreadPool(2)
    val timeout = threads.schedule({ process.destroy() }, 120, TimeUnit.SECONDS)
    fun capture(stream: InputStream): String = stream.bufferedReader().use { reader ->
        val text = StringBuilder()
        val buffer = CharArray(1024)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            if (text.length < 16384) text.append(buffer, 0, minOf(count, 16384 - text.length))
        }
        text.toString()
    }
    val errors = threads.submit<String> { capture(process.errorStream) }
    return try {
        process.outputStream.use { output -> input?.copyTo(output) }
        val output = capture(process.inputStream)
        val code = process.waitFor()
        ShellResult(code, output + errors.get(2, TimeUnit.SECONDS))
    } finally {
        timeout.cancel(false)
        process.destroy()
        errors.cancel(true)
        threads.shutdownNow()
    }
}
