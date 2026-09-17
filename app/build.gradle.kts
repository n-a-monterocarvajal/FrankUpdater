plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.github.n_a_monterocarvajal.frankupdater"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.n_a_monterocarvajal.frankupdater"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-alpha01"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:compatibility"))
    implementation(project(":core:archive"))
    implementation(libs.apksig.android)
    implementation(libs.gplayapi)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.jsoup)
    coreLibraryDesugaring(libs.desugar)

    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))

    implementation(libs.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
}

tasks.withType<Test>().configureEach {
    val liveWeb = providers.environmentVariable("FRANK_LIVE_WEB").orElse("0").get()
    inputs.property("liveWeb", liveWeb)
    environment("FRANK_LIVE_WEB", liveWeb)
    // External services change independently of source files; explicit live runs must not reuse results.
    if (liveWeb == "1") {
        outputs.upToDateWhen { false }
        outputs.doNotCacheIf("Explicit live provider check") { true }
    }
}
