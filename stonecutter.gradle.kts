plugins {
    id("dev.kikugie.stonecutter")
    id("fabric-loom") version "1.13.6" apply false
}

stonecutter active "1.21.10"

tasks.register("buildAll") {
    group = "build"
    description = "Builds every version listed in settings.gradle.kts."
    dependsOn(stonecutter.versions.map { ":${it.project}:build" })
}
