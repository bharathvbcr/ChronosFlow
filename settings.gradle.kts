pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ChronosFlow"

include(":app")
include(":benchmark")
include(":core:ai")
include(":core:data")
include(":core:domain")
include(":core:ui")
include(":core:notifications")
include(":feature:daydial")
include(":feature:focus")
include(":feature:tasks")
include(":feature:habits")
include(":feature:medication")
include(":wear")
