/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.sources

import io.github.n_a_monterocarvajal.frankupdater.compatibility.CatalogEntry
import io.github.n_a_monterocarvajal.frankupdater.compatibility.ReleaseChannel
import io.github.n_a_monterocarvajal.frankupdater.model.Source
import java.io.IOException
import java.io.InterruptedIOException

/** [mirrorApp] is the APKMirror app page that matched, to be remembered and passed back next time. */
internal class SourceLookup(val entries: List<CatalogEntry>, val failures: Map<Source, String>, val mirrorApp: String? = null) {
    val failedSources: Set<Source> get() = failures.keys
}

/**
 * Queries every web source that can be addressed by package name alone. Blocking; run on an IO thread.
 * Entries whose published signer differs from the installed one are dropped: they could never update the app.
 */
internal fun lookupSources(client: WebSourceClient, packageName: String, deviceSdk: Int,
    installedSigners: Set<String>, knownMirrorApp: String? = null): SourceLookup {
    requirePackageName(packageName)
    val entries = mutableListOf<CatalogEntry>()
    val failed = mutableMapOf<Source, String>()
    fun query(source: Source, block: () -> List<CatalogEntry>) {
        try { entries += block() }
        catch (interrupted: InterruptedIOException) {
            if (Thread.currentThread().isInterrupted) throw interrupted else failed[source] = interrupted.toString()
        }
        // A repository without the package answers 404: absent, not failed.
        catch (error: IOException) { if (error.message?.endsWith("HTTP 404.") != true) failed[source] = error.toString() }
        catch (error: Exception) { failed[source] = error.toString() }
    }
    query(Source.ApkPure) {
        val json = client.text("https://tapi.pureapk.com/v3/get_app_his_version?package_name=$packageName&hl=en",
            Source.ApkPure, mapOf("Ual-Access-Businessid" to "projecta", "Ual-Access-ProjectA" to
                "{\"device_info\":{\"os_ver\":\"$deviceSdk\"}}"))
        // An unknown package comes back as an empty version list: absent, not failed.
        if (Regex("\"version_list\"\\s*:\\s*\\[\\s*]").containsMatchIn(json)) emptyList() else PureParser.history(json, packageName)
    }
    for (repository in listOf(Source.FDroid, Source.IzzyOnDroid)) query(repository) {
        FdroidParser.history(client.text("${FdroidParser.repo(repository)}/api/v1/packages/$packageName", repository),
            packageName, repository)
    }
    var mirrorApp = knownMirrorApp
    query(Source.ApkMirror) {
        val (found, app) = mirrorEntries(client, packageName, installedSigners, knownMirrorApp)
        mirrorApp = app
        found
    }
    return SourceLookup(entries.filter { entry ->
        val signers = entry.artifact.signerDigests
        installedSigners.isEmpty() || signers.isEmpty() || signers.any(installedSigners::contains)
    }, failed, mirrorApp)
}

/**
 * Newest stable APKMirror release whose variants carry this package and, when known, the installed signer.
 * The same package can have several APKMirror apps (e.g. GitHub and F-Droid builds with different keys).
 * Site search is the most rate-limited endpoint, so it runs once per package; afterwards the remembered
 * app's RSS feed lists releases. Returns the entries and the app page to remember.
 * ponytail: up to 2 releases x 3 variant pages per package; paginate only if newest releases stop matching.
 */
private fun mirrorEntries(client: WebSourceClient, packageName: String, installedSigners: Set<String>,
    knownApp: String?): Pair<List<CatalogEntry>, String?> {
    val fromFeed = knownApp?.let { app ->
        runCatching { MirrorParser.feedReleases(MirrorPacer.text(client, app + "feed/")) }.getOrNull()
    }
    val releases = (fromFeed ?: run {
        val search = "https://www.apkmirror.com/?post_type=app_release&searchtype=apk&s=$packageName"
        MirrorParser.searchReleases(MirrorPacer.text(client, search), search)
    }).filter { releaseChannel(it.name) != ReleaseChannel.Preview }.take(2)
    for (release in releases) {
        val variants = MirrorParser.variants(MirrorPacer.text(client, release.url), release.url)
        val found = variants.take(3).mapNotNull { variant ->
            val details = runCatching { MirrorParser.variantDetails(MirrorPacer.text(client, variant.url), variant.url) }
                .getOrNull()?.takeIf { it.packageName == packageName } ?: return@mapNotNull null
            variant.catalogEntry(packageName, details)
        }
        if (found.any { installedSigners.isEmpty() || it.artifact.signerDigests.any(installedSigners::contains) }) {
            return found to MirrorParser.appUrl(release.url)
        }
    }
    return emptyList<CatalogEntry>() to knownApp
}

/**
 * APKMirror's Cloudflare answers bursts with HTTP 429 challenges (about a minute). Space HTML requests and,
 * after a 429, stop asking for a while instead of extending the block. Shared by the worker and Search.
 */
private object MirrorPacer {
    private const val SPACING_MS = 1_500L
    private const val BACKOFF_MS = 10 * 60_000L
    private var last = 0L
    private var blockedUntil = 0L

    @Synchronized fun text(client: WebSourceClient, url: String): String {
        val now = System.currentTimeMillis()
        if (now < blockedUntil) throw IOException("APKMirror limitó las consultas; se reintentará más tarde.")
        Thread.sleep((last + SPACING_MS - now).coerceAtLeast(0))
        last = System.currentTimeMillis()
        try { return client.text(url, Source.ApkMirror) }
        catch (error: IOException) {
            if (error.message?.endsWith("HTTP 429.") == true) blockedUntil = System.currentTimeMillis() + BACKOFF_MS
            throw error
        }
    }
}

internal val Source.label: String get() = when (this) {
    Source.GooglePlay -> "Google Play"
    Source.ApkMirror -> "APKMirror"
    Source.ApkPure -> "APKPure"
    Source.FDroid -> "F-Droid"
    Source.IzzyOnDroid -> "IzzyOnDroid"
}

/** Remembers which APKMirror app page matched each package, so later lookups use its feed instead of search. */
internal class MirrorAppStore(context: android.content.Context) {
    private val preferences = context.applicationContext.getSharedPreferences("apkmirror_apps", 0)
    operator fun get(packageName: String): String? = preferences.getString(packageName, null)
    fun remember(packageName: String, lookup: SourceLookup) { lookup.mirrorApp?.let { remember(packageName, it) } }
    fun remember(packageName: String, appUrl: String) { preferences.edit().putString(packageName, appUrl).apply() }
}
