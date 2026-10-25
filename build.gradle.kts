description = "Composite build for the WorldPanel Kotlin library"

tasks.register("buildAll") {
    group = "verification"
    description = "Builds and tests WorldPanel core and verified platform targets."
    dependsOn(
        gradle.includedBuild("core").task(":build"),
        gradle.includedBuild("neoforge-1.21.1").task(":build"),
        gradle.includedBuild("neoforge-26.2").task(":build"),
    )
}

tasks.register("testAll") {
    group = "verification"
    description = "Runs the unit tests across WorldPanel builds."
    dependsOn(
        gradle.includedBuild("core").task(":test"),
    )
}

tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Publishes core and verified platform artifacts to mavenLocal."
    dependsOn(
        gradle.includedBuild("core").task(":publishToMavenLocal"),
        gradle.includedBuild("neoforge-1.21.1").task(":publishToMavenLocal"),
        gradle.includedBuild("neoforge-26.2").task(":publishToMavenLocal"),
    )
}
