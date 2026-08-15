/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Binary manifest classification follows App Manager's ApkFile approach at
 * fc1e70074e8cf75c0619e526e688c16ad2e1e862. XML decoding is provided by
 * ARSCLib 1.4.0 at 8807c5f63a00028d57adbf6de86137afcee3d595
 * (Apache-2.0), while identity fields use apksig's public ApkUtils API.
 */
package io.github.n_a_monterocarvajal.frankupdater.verification

import com.android.apksig.apk.ApkUtils
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import com.reandroid.arsc.io.BlockReader
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipFile

internal data class BinaryManifestMetadata(
    val packageName: String,
    val versionCode: Long,
    val splitName: String?,
    val minSdk: Int?,
    val targetSdk: Int?,
)

internal class BinaryManifestMetadataReader {
    fun read(apk: File): BinaryManifestMetadata {
        val bytes = ZipFile(apk).use { zip ->
            val entry = zip.getEntry(ANDROID_MANIFEST)
                ?: error("El APK no contiene $ANDROID_MANIFEST")
            zip.getInputStream(entry).use { input -> input.readBytes() }
        }
        fun manifestBuffer(): ByteBuffer = ByteBuffer.wrap(bytes)
        return BinaryManifestMetadata(
            packageName = ApkUtils.getPackageNameFromBinaryAndroidManifest(manifestBuffer()),
            versionCode = ApkUtils.getLongVersionCodeFromBinaryAndroidManifest(manifestBuffer()),
            splitName = readSplitName(bytes),
            minSdk = runCatching {
                ApkUtils.getMinSdkVersionFromBinaryAndroidManifest(manifestBuffer())
            }.getOrNull(),
            targetSdk = runCatching {
                ApkUtils.getTargetSdkVersionFromBinaryAndroidManifest(manifestBuffer())
            }.getOrNull(),
        )
    }

    private fun readSplitName(bytes: ByteArray): String? =
        BlockReader(bytes).use { reader ->
            val document = ResXmlDocument().apply { readBytes(reader) }
            val root = document.documentElement
            require(root.name == MANIFEST_ELEMENT) { "El XML binario no tiene una raíz manifest." }
            val attributes = root.attributes
            while (attributes.hasNext()) {
                val attribute = attributes.next()
                if (attribute.name == SPLIT_ATTRIBUTE) {
                    return@use attribute.valueAsString?.takeIf(String::isNotBlank)
                }
            }
            null
        }

    private companion object {
        const val ANDROID_MANIFEST = "AndroidManifest.xml"
        const val MANIFEST_ELEMENT = "manifest"
        const val SPLIT_ATTRIBUTE = "split"
    }
}
