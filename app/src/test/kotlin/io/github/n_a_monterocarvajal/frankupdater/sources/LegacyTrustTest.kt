package io.github.n_a_monterocarvajal.frankupdater.sources

import java.security.MessageDigest
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyTrustTest {
    @Test fun `bundled root is the published ISRG Root X1 and joins the trusted issuers`() {
        val issuers = LegacyTrust.trustManager().acceptedIssuers
        val fingerprints = issuers.map { cert ->
            MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02X".format(it) }
        }
        assertTrue("96BCEC06264976F37460779ACF28C5A7CFE8A3C0AAE11A8FFCEE05C0BDDF08C6" in fingerprints)
        assertTrue(issuers.size > 1)
    }
}
