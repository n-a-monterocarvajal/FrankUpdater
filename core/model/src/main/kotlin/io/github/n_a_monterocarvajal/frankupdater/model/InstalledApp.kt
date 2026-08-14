package io.github.n_a_monterocarvajal.frankupdater.model

data class InstalledApp(
    val displayName: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val signingCertificateHistory: List<String>,
    val splitSourceDirs: List<String>,
    val installerSource: String?,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
)
