plugins {
	id("fabric-loom") version "1.13.6"
	id("maven-publish")
	id("org.jetbrains.kotlin.jvm") version "2.3.10"
}

val mod_version: String by project
val maven_group: String by project
val archives_base_name: String by project
val minecraft_version: String by project
val yarn_mappings: String by project
val loader_version: String by project
val fabric_version: String by project
val fabric_kotlin_version: String by project


base.archivesName.set(archives_base_name)
base {
	version = mod_version
	group = maven_group
}

repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
	mavenCentral()
	maven("https://maven.fabricmc.net/")
	maven("https://repo.essential.gg/repository/maven-public/")
	maven("https://maven.architectury.dev")
	maven("https://api.modrinth.com/maven")
}
fabricApi {
	configureDataGeneration {
		client.set(true)
	}
}

configurations.all {
	resolutionStrategy {
		force("gg.essential:universalcraft-1.21.9-fabric:449")
		force("gg.essential:elementa:710")
	}
}

dependencies {
	// Minecraft
	minecraft("com.mojang:minecraft:$minecraft_version")
	mappings("net.fabricmc:yarn:$yarn_mappings:v2")

	// Fabric
	modImplementation("net.fabricmc:fabric-loader:$loader_version")
	modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_version")
	modImplementation("net.fabricmc:fabric-language-kotlin:$fabric_kotlin_version")

	// Essentials
	modImplementation("gg.essential:universalcraft-1.21.9-fabric:449")
	include("gg.essential:universalcraft-1.21.9-fabric:449")
	implementation(("gg.essential:vigilance:312")) { // Kan !! erbij zetten, check docs
		exclude(group = "gg.essential", module = "elementa")
		exclude(group = "gg.essential", module = "universalcraft")
	}
	include("gg.essential:vigilance:312") {
		exclude(group = "gg.essential", module = "elementa")
		exclude(group = "gg.essential", module = "universalcraft")
	}
	implementation("gg.essential:elementa:710")
	include("gg.essential:elementa:710")

	// Kotlin
	implementation(kotlin("stdlib-jdk8"))

	// Firmament (compile-only for mixin targets)
	modCompileOnly("maven.modrinth:firmament:43.0.0+mc1.21.10")
}

tasks.processResources {
	inputs.property("version", mod_version)
	inputs.property("minecraft_version", minecraft_version)
	inputs.property("fabric_version", fabric_version)
	inputs.property("loader_version", loader_version)
	inputs.property("fabric_kotlin_version", fabric_kotlin_version)

	filesMatching("fabric.mod.json") {
		expand(
			mapOf(
				"version" to mod_version,
				"minecraft_version" to minecraft_version,
				"fabric_version" to fabric_version,
				"loader_version" to loader_version,
			)
		)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release.set(21)
}


java {
	withSourcesJar()
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
	compilerOptions {
		suppressWarnings.set(true)
		jvmToolchain(21)
	}
}

tasks.jar {
	from("LICENSE") {
		rename { "${it}_${archives_base_name}"}
	}
}