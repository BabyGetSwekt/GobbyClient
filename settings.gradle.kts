pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.minecraftforge.net/")
		maven("https://maven.fabricmc.net/")
		maven("https://repo.essential.gg/repository/maven-public/")
		maven("https://maven.architectury.dev")
	}
	plugins {
		kotlin("jvm") version "2.3.10"
		id("gg.essential.loom") version "0.10.0.3"
		id("io.github.juuxel.loom-quiltflower") version "1.7.3"
		id("dev.architectury.architectury-pack200") version "0.1.3"
		id("com.github.johnrengelman.shadow") version "7.1.2"
	}
}

