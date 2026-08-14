package io.github.n_a_monterocarvajal.frankupdater.model

data class GenericDeviceProfile(
    val sdk: Int,
    val codename: String?,
    val supportedAbis: List<String>,
    val densityDpi: Int,
    val locales: List<String>,
    val systemFeatures: Set<String>,
)
data class PlayDeviceProfile(
    val generic: GenericDeviceProfile,
    val fingerprint: String,
    val manufacturer: String,
    val model: String,
)
