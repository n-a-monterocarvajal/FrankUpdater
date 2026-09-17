/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.sources

import com.google.gson.JsonParser
import io.github.n_a_monterocarvajal.frankupdater.compatibility.CatalogEntry
import io.github.n_a_monterocarvajal.frankupdater.compatibility.ReleaseChannel
import io.github.n_a_monterocarvajal.frankupdater.model.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

internal data class WebRelease(val name: String, val url: String)
internal data class MirrorVariant(
    val name: String, val architecture: String, val minimumAndroid: String,
    val density: String, val url: String, val type: PackageType, val versionCode: Long?,
    val channel: ReleaseChannel = ReleaseChannel.Unknown,
)

internal fun releaseChannel(label: String): ReleaseChannel =
    if (Regex("(?i)\\b(alpha|beta|early[ -]access|preview|rc[0-9]*)\\b").containsMatchIn(label))
        ReleaseChannel.Preview else ReleaseChannel.Unknown

/** Ports APKMD table selectors; intentionally does not port getFilteredVariant. */
internal object MirrorParser {
    fun releases(html: String, url: String): List<WebRelease> {
        val document = document(html, url)
        val table = requireNotNull(document.selectFirst(".listWidget:has(a[name=all_versions])"))
        return table.select(".table-cell a[href]").mapNotNull { link ->
            val target = link.absUrl("href")
            if (!target.contains("-release/") || link.text().isBlank()) null
            else WebRelease(link.text(), sourceUrl(target, Source.ApkMirror).toString())
        }.distinctBy { it.url }.take(200).also { require(it.isNotEmpty()) }
    }

    fun variants(html: String, url: String): List<MirrorVariant> {
        val parsed = document(html, url)
        val channel = releaseChannel(parsed.title() + " " + parsed.select("h1").text() + " " + url)
        val table = requireNotNull(parsed.selectFirst(".variants-table"))
        return table.children().mapNotNull { row ->
            val cells = row.select(".table-cell")
            val link = cells.firstOrNull()?.selectFirst("a[href]") ?: return@mapNotNull null
            if (cells.size < 4) return@mapNotNull null
            val type = when {
                cells[0].text().contains("BUNDLE", true) -> PackageType.Apkm
                cells[0].text().contains("APK", true) -> PackageType.MonolithicApk
                else -> return@mapNotNull null
            }
            val code = (cells[0].selectFirst(".colorLightBlack")?.ownText()?.trim()?.toLongOrNull()
                ?: Regex("\\((\\d+)\\)").find(link.text())?.groupValues?.get(1)?.toLongOrNull())?.takeIf { it > 0 }
            MirrorVariant(link.text(), cells[1].text(), cells[2].text(), cells[3].text(),
                sourceUrl(link.absUrl("href"), Source.ApkMirror).toString(), type, code,
                if (channel == ReleaseChannel.Preview) channel else releaseChannel(link.text()))
        }.distinct().take(200).also { require(it.isNotEmpty()) }
    }

    fun downloadPage(html: String, url: String): String = sourceUrl(
        requireNotNull(document(html, url).selectFirst("a.downloadButton[href]")).absUrl("href"),
        Source.ApkMirror,
    ).toString()

    fun downloadUrl(html: String, url: String): String = sourceUrl(
        requireNotNull(document(html, url).selectFirst(".card-with-tabs a[href]" )).absUrl("href"),
        Source.ApkMirror, download = true,
    ).toString()

    private fun document(html: String, url: String): org.jsoup.nodes.Document {
        require(html.length <= 8 * 1024 * 1024 && !html.contains("Enable JavaScript and cookies to continue"))
        return Jsoup.parse(html, sourceUrl(url, Source.ApkMirror).toString())
    }
}

internal fun MirrorVariant.catalogEntry(packageName: String): CatalogEntry? {
    val code = versionCode ?: return null
    val abis = architecture.split('+', ',').map(String::trim).filter(String::isNotBlank)
    return CatalogEntry(ArtifactCandidate(packageName, name, code, Source.ApkMirror,
        minSdk = Regex("API\\s+(\\d+)", RegexOption.IGNORE_CASE).find(minimumAndroid)?.groupValues?.get(1)?.toIntOrNull()
            ?: androidSdkFromLabel(minimumAndroid),
        maxSdk = null, targetSdk = null, abis = if (abis.any { it.equals("universal", true) || it.equals("noarch", true) }) emptyList() else abis,
        densityDpi = null, locales = emptyList(), requiredFeatures = emptySet(), packageType = type,
        signerDigests = emptySet(), artifacts = emptyList(), metadataUrl = url, downloadMode = DownloadMode.ResolvableDirect), channel = channel)
}

/** Only recognized Android release labels; unknown labels remain unknown, never a float comparison. */
private fun androidSdkFromLabel(label: String): Int? {
    val version = Regex("Android\\s+(\\d+(?:\\.\\d+)?L?)\\+?", RegexOption.IGNORE_CASE)
        .find(label)?.groupValues?.get(1)?.uppercase() ?: return null
    return when (version) {
        "6", "6.0" -> 23
        "7", "7.0" -> 24
        "7.1" -> 25
        "8", "8.0" -> 26
        "8.1" -> 27
        "9", "9.0" -> 28
        "10", "10.0" -> 29
        "11", "11.0" -> 30
        "12", "12.0" -> 31
        "12L", "12.1" -> 32
        "13", "13.0" -> 33
        "14", "14.0" -> 34
        "15", "15.0" -> 35
        "16", "16.0" -> 36
        else -> null
    }
}

/** Obtainium's version_list and asset contract, retaining every numeric version and ABI variant. */
internal object PureParser {
    fun history(json: String, packageName: String): List<CatalogEntry> {
        requirePackageName(packageName)
        require(json.length <= 8 * 1024 * 1024)
        val rows = JsonParser.parseString(json).asJsonObject.getAsJsonArray("version_list")
        require(rows != null && rows.size() <= 2000)
        return rows.mapNotNull { element ->
            val row = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            fun string(key: String) = row.get(key)?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isBoolean }?.asString
            if (string("package_name") != packageName) return@mapNotNull null
            val code = string("version_code")?.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val asset = row.get("asset")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val type = when (asset.get("type")?.takeIf { it.isJsonPrimitive }?.asString?.uppercase()) {
                "APK" -> PackageType.MonolithicApk
                "XAPK" -> PackageType.Xapk
                else -> return@mapNotNull null
            }
            val download = asset.get("url")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
            val safeUrl = runCatching { sourceUrl(download, Source.ApkPure, download = true).toString() }.getOrNull()
                ?: return@mapNotNull null
            fun assetString(key: String) = asset.get(key)?.takeIf { it.isJsonPrimitive }?.asString
            val hash = assetString("file_sha256")?.takeIf(String::isNotBlank)
            if (hash != null && !Regex("[a-fA-F0-9]{64}").matches(hash)) return@mapNotNull null
            val size = assetString("size")?.toLongOrNull()
            if (size != null && size <= 0) return@mapNotNull null
            val abis = row.get("native_code")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { it.takeIf { value -> value.isJsonPrimitive && value.asJsonPrimitive.isString }?.asString }.orEmpty()
            CatalogEntry(ArtifactCandidate(packageName, string("version_name"), code, Source.ApkPure,
                minSdk = string("sdk_version")?.toIntOrNull()?.takeIf { it > 0 }, maxSdk = null,
                targetSdk = string("target_sdk_version")?.toIntOrNull()?.takeIf { it > 0 },
                abis = if (abis.any { it == "universal" || it == "unlimited" }) emptyList() else abis,
                densityDpi = null, locales = emptyList(), requiredFeatures = emptySet(), packageType = type,
                signerDigests = emptySet(), artifacts = listOf(RemoteArtifact(
                    if (type == PackageType.Xapk) "download.xapk" else "download.apk", safeUrl, hash, size)),
                metadataUrl = "https://apkpure.com/apk/$packageName/versions", downloadMode = DownloadMode.Direct),
                channel = releaseChannel(string("version_name").orEmpty()))
        }.distinct().sortedByDescending { it.artifact.versionCode }.also { require(it.isNotEmpty()) }
    }
}

internal fun requirePackageName(value: String) {
    require(value.length <= 255 && Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(value))
}

/** Exact domain boundaries, HTTPS and no embedded credentials; rechecked on every redirect. */
internal fun sourceUrl(value: String, source: Source, download: Boolean = false): HttpUrl = value.toHttpUrl().also { url ->
    val roots = when (source) {
        Source.ApkMirror -> listOf("apkmirror.com")
        Source.ApkPure -> if (download) listOf("apkpure.com", "apkpure.net", "pureapk.com", "winudf.com")
            else listOf("apkpure.com", "apkpure.net", "pureapk.com")
        else -> error("Fuente web inválida.")
    }
    // Exact bucket observed in APKMirror's HTTPS redirect; never trust the shared R2 parent domain.
    val mirrorCdn = download && source == Source.ApkMirror &&
        url.host == "eb5e7388c3df147b74dd2379b7cf8323.r2.cloudflarestorage.com"
    require(url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
        (mirrorCdn || roots.any { url.host == it || url.host.endsWith(".$it") })) { "Dirección de fuente inválida; host=${url.host}" }
}
