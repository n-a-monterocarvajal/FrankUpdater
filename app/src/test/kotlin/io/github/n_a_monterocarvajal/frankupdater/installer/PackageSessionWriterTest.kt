package io.github.n_a_monterocarvajal.frankupdater.installer

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PackageSessionWriterTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("frank-session-test").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `writes base and every split in order and fsyncs each one`() {
        val base = File(directory, "base.apk").apply { writeText("base") }
        val abi = File(directory, "config.arm64.apk").apply { writeText("abi") }
        val locale = File(directory, "config.es.apk").apply { writeText("locale") }
        val session = RecordingSession()

        PackageSessionWriter().write(
            listOf(
                SessionApk("base.apk", base),
                SessionApk("001_config.arm64.apk", abi),
                SessionApk("002_config.es.apk", locale),
            ),
            session,
        )

        assertEquals(
            listOf("base.apk", "001_config.arm64.apk", "002_config.es.apk"),
            session.names,
        )
        assertEquals(listOf(4L, 3L, 6L), session.lengths)
        assertEquals(3, session.fsyncCount)
        assertArrayEquals("base".toByteArray(), session.outputs[0].toByteArray())
        assertArrayEquals("abi".toByteArray(), session.outputs[1].toByteArray())
        assertArrayEquals("locale".toByteArray(), session.outputs[2].toByteArray())
    }

    private class RecordingSession : InstallSessionHandle {
        val names = mutableListOf<String>()
        val lengths = mutableListOf<Long>()
        val outputs = mutableListOf<ByteArrayOutputStream>()
        var fsyncCount = 0

        override fun openWrite(name: String, lengthBytes: Long): OutputStream {
            names += name
            lengths += lengthBytes
            return ByteArrayOutputStream().also(outputs::add)
        }

        override fun fsync(output: OutputStream) {
            fsyncCount += 1
        }
    }
}
