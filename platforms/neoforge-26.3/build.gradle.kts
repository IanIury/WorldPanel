import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.4.21"
    id("net.neoforged.moddev") version "2.0.148"
}

group = "dev.ian.worldpanel"
version = providers.gradleProperty("worldpanel_version").get()

base {
    archivesName.set("worldpanel-neoforge-26.3")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

kotlin {
    jvmToolchain(25)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
}

repositories {
    maven("https://repo.nyon.dev/releases") {
        content { includeGroup("dev.nyon") }
    }
}

neoForge {
    version = providers.gradleProperty("neoforge_version").get()
}

dependencies {
    api("dev.ian.worldpanel:worldpanel-core:${providers.gradleProperty("worldpanel_version").get()}")

    // KLF is compile-only: library has no @Mod entrypoint or direct KLF dependencies
    compileOnly("dev.nyon:KotlinLangForge:${providers.gradleProperty("kotlinlangforge_version").get()}")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "worldpanel-neoforge-26.3"
        }
    }
}
