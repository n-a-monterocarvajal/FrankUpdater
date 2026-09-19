/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.sources

import io.github.n_a_monterocarvajal.frankupdater.model.Source
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Reads the signer of a remote APK without downloading it: the ZIP end of central directory gives the
 * central directory offset, and the APK Signing Block sits right before it (APK Signature Scheme v2/v3 format).
 * Only the first signer's certificate is extracted, as an identity hint to rank candidates. It is not a
 * verification: the downloaded file still goes through full signature verification before installation.
 */
internal object ApkSigningBlock {
    private const val EOCD_SIGNATURE = 0x06054b50
    private const val EOCD_MIN_SIZE = 22
    private const val MAX_COMMENT = 0xffff
    private const val FOOTER_SIZE = 24
    private const val MAX_BLOCK = 4 * 1024 * 1024
    private val MAGIC = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
    // v3.1 and v3 carry the current (possibly rotated) signer; v2 the original one.
    private val SCHEME_IDS = listOf(0x1b93ad61, 0xf05368c0.toInt(), 0x7109871a)

    /** Suffix length that always contains the end of central directory record. */
    const val TAIL = EOCD_MIN_SIZE + MAX_COMMENT

    /** Central directory offset from the file tail, or null if the tail holds no end of central directory. */
    fun centralDirectoryOffset(tail: ByteArray): Long? {
        val buffer = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
        for (position in tail.size - EOCD_MIN_SIZE downTo maxOf(0, tail.size - TAIL)) {
            if (buffer.getInt(position) == EOCD_SIGNATURE &&
                (buffer.getShort(position + 20).toInt() and 0xffff) == tail.size - position - EOCD_MIN_SIZE) {
                return buffer.getInt(position + 16).toLong() and 0xffffffffL
            }
        }
        return null
    }

    /** File range `[start, centralDirectory)` of the signing block from its 24-byte footer, or null without one. */
    fun blockStart(footer: ByteArray, centralDirectory: Long): Long? {
        require(footer.size == FOOTER_SIZE)
        if (!footer.copyOfRange(8, 24).contentEquals(MAGIC)) return null
        val size = ByteBuffer.wrap(footer).order(ByteOrder.LITTLE_ENDIAN).getLong(0)
        require(size in FOOTER_SIZE..MAX_BLOCK.toLong())
        return centralDirectory - size - 8
    }

    /** DER certificate of the first signer, preferring the newest scheme present. */
    fun firstCertificate(block: ByteArray): ByteArray? {
        val buffer = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)
        val end = block.size - FOOTER_SIZE
        buffer.position(8)
        val schemes = mutableMapOf<Int, ByteBuffer>()
        while (buffer.position() + 12 <= end) {
            val length = buffer.getLong()
            require(length in 4..(end - buffer.position()).toLong())
            val id = buffer.getInt()
            schemes[id] = slice(buffer, (length - 4).toInt())
        }
        val value = SCHEME_IDS.firstNotNullOfOrNull(schemes::get) ?: return null
        val signers = lengthPrefixed(value)
        val signer = lengthPrefixed(signers)
        val signedData = lengthPrefixed(signer)
        lengthPrefixed(signedData) // digests
        val certificates = lengthPrefixed(signedData)
        val certificate = lengthPrefixed(certificates)
        return ByteArray(certificate.remaining()).also(certificate::get)
    }

    private fun lengthPrefixed(buffer: ByteBuffer): ByteBuffer {
        val length = buffer.getInt()
        require(length in 0..buffer.remaining())
        return slice(buffer, length)
    }

    private fun slice(buffer: ByteBuffer, length: Int): ByteBuffer {
        val slice = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
        slice.limit(length)
        buffer.position(buffer.position() + length)
        return slice
    }
}

/**
 * SHA-256 of the first signer certificate of a remote APK, read with two or three small range requests.
 * Returns null when the APK has no v2/v3 block (v1-only) or the server does not serve ranges.
 */
internal fun remoteSignerSha256(client: WebSourceClient, url: String, source: Source): String? = runCatching {
    val (tailStart, tail) = client.range(url, source, "bytes=-${ApkSigningBlock.TAIL}", ApkSigningBlock.TAIL)
    val centralDirectory = requireNotNull(ApkSigningBlock.centralDirectoryOffset(tail))
    fun read(from: Long, until: Long): ByteArray =
        if (from >= tailStart) tail.copyOfRange((from - tailStart).toInt(), (until - tailStart).toInt())
        else client.range(url, source, "bytes=$from-${until - 1}", (until - from).toInt()).second
    val start = ApkSigningBlock.blockStart(read(centralDirectory - 24, centralDirectory), centralDirectory)
        ?: return@runCatching null
    val certificate = ApkSigningBlock.firstCertificate(read(start, centralDirectory)) ?: return@runCatching null
    MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02x".format(it) }
}.getOrNull()
