package io.github.n_a_monterocarvajal.frankupdater.model

data class GenericDeviceProfile(
    val sdk: Int,
    val codename: String?,
    val supportedAbis: List<String>,
    val densityDpi: Int,
    val locales: List<String>,
    val systemFeatures: Set<String>,
    val androidRelease: String? = null,
    val screenWidthPixels: Int = 0,
    val screenHeightPixels: Int = 0,
    val sharedLibraries: Set<String> = emptySet(),
    val glEsVersion: Int = 0,
)

data class PlayDeviceProfile(
    val generic: GenericDeviceProfile,
    val fingerprint: String,
    val manufacturer: String,
    val model: String,
    val brand: String,
    val product: String,
    val device: String,
    val buildId: String,
    val gsfVersionCode: Long? = null,
    val vendingVersionCode: Long? = null,
    val vendingVersionName: String? = null,
)
