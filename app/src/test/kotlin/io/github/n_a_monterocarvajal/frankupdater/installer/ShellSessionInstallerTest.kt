package io.github.n_a_monterocarvajal.frankupdater.installer

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ShellSessionInstallerTest {
    @Test fun `one session receives every APK in order before commit and abandons failures`() {
        val directory = Files.createTempDirectory("shell-install-test").toFile()
        try {
            val files = listOf(File(directory, "base with spaces.apk"), File(directory, "split.apk"))
            files.forEach { it.writeText("fixture") }
            for (failWrite in listOf(false, true)) {
                val commands = mutableListOf<String>()
                val written = mutableListOf<File>()
                val installer = ShellSessionInstaller { args, file ->
                    commands += args[1]
                    assertFalse(args.any { it.contains(directory.path) })
                    file?.let { written += it }
                    when (args[1]) {
                        "install-create" -> ShellResult(0, "Success: created install session [42]")
                        "install-write" -> if (failWrite) ShellResult(1, "Failure") else ShellResult(0, "Success: streamed")
                        else -> ShellResult(0, "Success")
                    }
                }
                if (failWrite) {
                    assertThrows(IllegalStateException::class.java) { installer.install(files, "org.example.app", 0) }
                    assertEquals(listOf("install-create", "install-write", "install-abandon"), commands)
                } else {
                    assertEquals(42, installer.install(files, "org.example.app", 0))
                    assertEquals(files, written)
                    assertEquals(listOf("install-create", "install-write", "install-write", "install-commit"), commands)
                }
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun `automatic selection never requests root and explicit modes stay explicit`() {
        assertEquals(InstallerMode.System, selectedInstaller(InstallerMode.Automatic, false))
        assertEquals(InstallerMode.Shizuku, selectedInstaller(InstallerMode.Automatic, true))
        assertEquals(InstallerMode.Root, selectedInstaller(InstallerMode.Root, false))
        assertEquals(InstallerMode.Legacy, selectedInstaller(InstallerMode.Legacy, true))
    }
}
