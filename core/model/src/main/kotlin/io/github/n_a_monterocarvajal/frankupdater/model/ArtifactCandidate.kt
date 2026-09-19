package io.github.n_a_monterocarvajal.frankupdater.model

enum class Source {
    GooglePlay,
    ApkMirror,
    ApkPure,
    FDroid,
    IzzyOnDroid,
}

enum class PackageType {
    MonolithicApk,
    SplitApkSet,
    Apkm,
    Xapk,
}

enum class DownloadMode {
    Direct,
    ResolvableDirect,
    AssistedWeb,
    ExternalBrowser,
    Unavailable,
}

data class RemoteArtifact(
    val name: String,
    val uri: String,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
)
data class ArtifactCandidate(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val source: Source,
    val minSdk: Int?,
    val maxSdk: Int?,
    val targetSdk: Int?,
    val abis: List<String>,
    val densityDpi: Int?,
    val locales: List<String>,
    val requiredFeatures: Set<String>,
    val packageType: PackageType,
    val signerDigests: Set<String>,
    val artifacts: List<RemoteArtifact>,
    val metadataUrl: String?,
    val downloadMode: DownloadMode,
)
