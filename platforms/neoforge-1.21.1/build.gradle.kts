import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.1.20"
    id("net.neoforged.moddev") version "2.0.144"
}

group = "dev.ian.worldpanel"
version = providers.gradleProperty("worldpanel_version").get()

base {
    archivesName.set("worldpanel-neoforge-1.21.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

sourceSets.main {
    kotlin.srcDir("../render-common/src/main/kotlin")
    kotlin.srcDir("../../core/src/main/kotlin")
}

repositories {
    maven("https://thedarkcolour.github.io/KotlinForForge/") {
        content { includeGroup("thedarkcolour") }
    }
}

neoForge {
    version = providers.gradleProperty("neoforge_version").get()
}

dependencies {
    // Resolution/build verification only. The library has no KFF API references;
    // consumers choose and install their Kotlin runtime themselves.
    compileOnly("thedarkcolour:kotlinforforge-neoforge:${providers.gradleProperty("kotlinforforge_version").get()}")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "worldpanel-neoforge-1.21.1"
        }
    }
}
