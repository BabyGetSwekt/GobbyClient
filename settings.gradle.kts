pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.fabricmc.net/")
        maven("https://repo.essential.gg/repository/maven-public/")
        maven("https://maven.architectury.dev")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
    plugins {
        kotlin("jvm") version "2.3.10"
        id("gg.essential.loom") version "0.10.0.3"
        id("io.github.juuxel.loom-quiltflower") version "1.7.3"
        id("dev.architectury.architectury-pack200") version "0.1.3"
        id("com.github.johnrengelman.shadow") version "7.1.2"
        id("dev.kikugie.stonecutter") version "0.9.1"
    }
}

plugins {
    id("dev.kikugie.stonecutter")
}

rootProject.name = "GobbyClient-Fabric"
rootProject.buildFileName = "stonecutter.gradle.kts"

stonecutter {
    kotlinController = true
    create(rootProject) {
        listOf("1.21.10", "1.21.11").forEach { version(it, it) }
        vcsVersion = "1.21.10"
    }
}
