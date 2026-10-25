pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "WorldPanel"

// Each platform target is an isolated included build to preserve independent
// Java, Kotlin and ModDevGradle toolchains without API contamination.
includeBuild("core")
includeBuild("platforms/neoforge-1.21.1")
includeBuild("platforms/neoforge-26.2")
