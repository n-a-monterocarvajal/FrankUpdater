package io.github.n_a_monterocarvajal.frankupdater.inventory

import org.junit.Assert.assertEquals
import org.junit.Test

class CertificateDigestTest {
    @Test
    fun `certificate digest is deterministic lowercase sha256`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "abc".encodeToByteArray().sha256Hex(),
        )
    }
}
