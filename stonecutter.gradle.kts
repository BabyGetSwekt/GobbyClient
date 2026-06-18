plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom") version "1.17.10" apply false
}

stonecutter active "26.2-rc-1"

tasks.register("buildAll") {
    group = "build"
    description = "Builds every version listed in settings.gradle.kts."
    dependsOn(stonecutter.versions.map { ":${it.project}:build" })
}
