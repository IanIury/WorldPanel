import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.1.20"
}

group = "dev.ian.worldpanel"
version = providers.gradleProperty("worldpanel_version").get()

base {
    archivesName.set("worldpanel-core")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

repositories {
    mavenCentral()
}

dependencies {
    // The integration runtime (KFF or KLF) supplies this. Keeping it compile-only
    // prevents the core POM from forcing either runtime on consumers.
    compileOnly(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
