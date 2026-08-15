pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.MuntashirAkon")
            }
        }
    }
}

rootProject.name = "FrankUpdater"

include(":app")
include(":core:model")
include(":core:compatibility")
include(":core:archive")
