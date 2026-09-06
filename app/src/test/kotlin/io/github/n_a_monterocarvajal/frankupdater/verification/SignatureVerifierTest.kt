package io.github.n_a_monterocarvajal.frankupdater.verification

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SignatureVerifierTest {
    private val verifier = SignatureVerifier()
    private fun fixture(name: String) = File(requireNotNull(javaClass.getResource("/signatures/$name")).toURI())
    private val original get() = fixture("golden-aligned-v1v2v3-out.apk")
    private val rotated get() = fixture("golden-aligned-v1v2v3-lineage-out.apk")

    @Test fun `valid APK verifies on old and modern Android`() {
        val old = verifier.verify(original, 23)
        assertEquals(1, old.size)
        assertEquals(old, verifier.verify(original, 37, old))
        assertTrue(old.single().matches(Regex("[0-9a-f]{64}")))
    }

    @Test fun `verified forward rotation is accepted but rollback is rejected`() {
        val old = verifier.verify(original, 28)
        val current = verifier.verify(rotated, 28, old)
        assertNotEquals(old, current)
        assertEquals(old, verifier.verify(rotated, 23, old))
        assertThrows(IllegalArgumentException::class.java) { verifier.verify(original, 28, current) }
    }

    @Test fun `multiple signers require an exact set and missing signers never authorize updates`() {
        val multi = fixture("v1-only-two-signers.apk")
        val signers = verifier.verify(multi, 28)
        assertEquals(2, signers.size)
        assertEquals(signers, verifier.verify(multi, 28, signers))
        assertThrows(IllegalArgumentException::class.java) { verifier.verify(multi, 28, setOf(signers.first())) }
        assertThrows(IllegalArgumentException::class.java) { verifier.verify(original, 28, emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { verifier.verify(original, 28, setOf("00".repeat(32))) }
    }

    @Test fun `modified bytes cannot pass verification`() {
        val apk = Files.createTempFile("tampered-", ".apk").toFile()
        try {
            val bytes = original.readBytes()
            bytes[100] = (bytes[100].toInt() xor 1).toByte()
            apk.writeBytes(bytes)
            assertThrows(Exception::class.java) { verifier.verify(apk, 28) }
        } finally { apk.delete() }
    }
}
