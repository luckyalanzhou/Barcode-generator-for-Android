pluginManagement {
    repositories {
        google {
            content { excludeGroupByRegex("org[.]jetbrains[.]kotlin([.].*)?") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.android.gms.oss-licenses-plugin") {
                useModule("com.google.android.gms:oss-licenses-plugin:0.13.0")
            }
        }
    }
}
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "BarcodeGenerator"
include(":app")
include(":core:domain")
include(":core:data")
include(":core:lan-share")
include(":architecture-tests")
