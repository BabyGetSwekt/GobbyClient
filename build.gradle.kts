import java.io.File

plugins {
	id("net.fabricmc.fabric-loom") version "1.17.10"
	id("maven-publish")
	id("org.jetbrains.kotlin.jvm") version "2.3.10"
}

val mod_version: String by project
val maven_group: String by project
val archives_base_name: String by project
val minecraft_version: String by project
val minecraft_depends: String by project
val loader_version: String by project
val fabric_version: String by project
val fabric_kotlin_version: String by project
val universalcraft_artifact: String by project
val universalcraft_version: String by project
val universalcraft = "gg.essential:$universalcraft_artifact:$universalcraft_version"


base.archivesName.set(archives_base_name)
base {
	version = "$mod_version+$minecraft_version"
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
		force(universalcraft)
		force("gg.essential:elementa:710")
	}
}

dependencies {
	// Minecraft
	minecraft("com.mojang:minecraft:$minecraft_version")

	// Fabric
	implementation("net.fabricmc:fabric-loader:$loader_version")
	implementation("net.fabricmc.fabric-api:fabric-api:$fabric_version")
	implementation("net.fabricmc:fabric-language-kotlin:$fabric_kotlin_version")

	// Essentials
	implementation(universalcraft)
	include(universalcraft)
	implementation("gg.essential:elementa:710")
	include("gg.essential:elementa:710")

	// Kotlin
	implementation(kotlin("stdlib-jdk8"))

	// Tests
	testImplementation(kotlin("test"))
	testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
	useJUnitPlatform()
}

val maxHandwrittenLines = 400
val maxUtilLines = 1000
val maxFunctionLines = 40

val verifyPathfinderStructure by tasks.registering {
	val sourceRoots = listOf(
		layout.projectDirectory.dir("src/main/kotlin").asFile,
		layout.projectDirectory.dir("src/test/kotlin").asFile,
		layout.projectDirectory.dir("src/main/java").asFile,
		layout.projectDirectory.dir("src/test/java").asFile,
	)
	doLast {
		val violations = sourceRoots.asSequence()
			.filter(File::exists)
			.flatMap { root -> root.walkTopDown() }
			.filter { it.isFile && it.extension in setOf("kt", "java") }
			.mapNotNull { file ->
				val lines = file.readLines().size
				val limit = if (isUtilFile(file)) maxUtilLines else maxHandwrittenLines
				file.takeIf { lines > limit }?.let { "$file has $lines physical lines, maximum is $limit" }
			}
			.toList()
		val functionViolations = sourceRoots.asSequence()
			.filter(File::exists)
			.flatMap { root -> root.walkTopDown() }
			.filter { it.isFile && it.extension == "kt" && isUtilFile(it) }
			.flatMap { file -> oversizedFunctions(file).asSequence() }
			.toList()
		if (violations.isNotEmpty() || functionViolations.isNotEmpty()) {
			throw GradleException((violations + functionViolations).joinToString(System.lineSeparator()))
		}
	}
}

fun isUtilFile(file: File): Boolean = file.path.contains("\\utils\\") || file.path.contains("/utils/")

fun oversizedFunctions(file: File): List<String> {
	val lines = file.readLines()
	val result = mutableListOf<String>()
	val declaration = Regex("^\\s*(?:(?:public|private|internal|protected|override|tailrec|suspend|inline|operator|infix|open)\\s+)*fun\\s+[A-Za-z_][A-Za-z0-9_]*")
	lines.forEachIndexed { index, line ->
		if (!declaration.containsMatchIn(line)) return@forEachIndexed
		val end = functionEnd(lines, index)
		val size = end - index + 1
		if (size > maxFunctionLines) result += "$file:${index + 1} has $size function lines, maximum is $maxFunctionLines"
	}
	return result
}

fun functionEnd(lines: List<String>, start: Int): Int {
	var depth = lines.take(start).sumOf { line ->
		val clean = line.substringBefore("//")
		clean.count { it == '{' } - clean.count { it == '}' }
	}
	val baseDepth = depth
	val declaration = Regex("^\\s*(?:(?:public|private|internal|protected|override|tailrec|suspend|inline|operator|infix|open)\\s+)*fun\\s+[A-Za-z_][A-Za-z0-9_]*")
	val expressionBody = Regex(".*\\)\\s*(?::\\s*[^=]+)?=\\s*.*").matches(lines[start].trim())
	if (expressionBody && !lines[start].trim().endsWith("=")) return start
	var opened = false
	for (index in start until lines.size) {
		if (index > start && (expressionBody || !opened) && declaration.containsMatchIn(lines[index])) return index - 1
		if (expressionBody) continue
		val clean = lines[index].substringBefore("//")
		depth += clean.count { it == '{' } - clean.count { it == '}' }
		if (clean.contains('{')) opened = true
		if (opened && depth <= baseDepth) return index
	}
	return lines.lastIndex
}

tasks.named("check") {
	dependsOn(verifyPathfinderStructure)
}

val generateBuildConfig by tasks.registering {
	val outputDir = layout.buildDirectory.dir("generated/sources/buildconfig/kotlin")
	outputs.dir(outputDir)
	inputs.property("mod_version", mod_version)
	doLast {
		val dir = outputDir.get().asFile.resolve("gobby")
		dir.mkdirs()
		dir.resolve("BuildConfig.kt").writeText(
			"""
			|package gobby
			|
			|object BuildConfig {
			|    const val MOD_VERSION = "$mod_version"
			|}
			""".trimMargin()
		)
	}
}

sourceSets.main {
	kotlin.srcDir(generateBuildConfig.map { layout.buildDirectory.dir("generated/sources/buildconfig/kotlin") })
}

tasks.compileKotlin {
	dependsOn(generateBuildConfig)
}

tasks.processResources {
	inputs.property("version", mod_version)
	inputs.property("minecraft_version", minecraft_version)
	inputs.property("minecraft_depends", minecraft_depends)
	inputs.property("fabric_version", fabric_version)
	inputs.property("loader_version", loader_version)
	inputs.property("fabric_kotlin_version", fabric_kotlin_version)

	filesMatching("fabric.mod.json") {
		expand(
			mapOf(
				"version" to mod_version,
				"minecraft_version" to minecraft_version,
				"minecraft_depends" to minecraft_depends,
				"fabric_version" to fabric_version,
				"loader_version" to loader_version,
			)
		)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release.set(25)
}


java {
	withSourcesJar()
	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
	compilerOptions {
		suppressWarnings.set(true)
		jvmToolchain(25)
	}
}

tasks.jar {
	from("LICENSE") {
		rename { "${it}_${archives_base_name}"}
	}
}
