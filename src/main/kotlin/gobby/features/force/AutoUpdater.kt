package gobby.features.force

import gobby.Gobbyclient.Companion.logger
import gobby.Gobbyclient.Companion.scope
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.HttpUtils
import gobby.config.UpdaterConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI

object AutoUpdater {

    private const val RELEASES_URL = "https://api.github.com/repos/BabyGetSwekt/GobbyClient/releases/latest"
    private val VERSION_REGEX = Regex("""GobbyClient\s+([\d.]+)""")

    private val modsDir = FabricLoader.getInstance().gameDir.resolve("mods").toFile()
    private val tempBatDir = File(UpdaterConfig.updatesFolder, "temp").also { it.mkdirs() }

    private var updateChecked = false

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        if (updateChecked) return
        updateChecked = true
        scope.launch(Dispatchers.IO) { checkForUpdates() }
    }

    private suspend fun checkForUpdates() {
        try {
            val json = HttpUtils.getString(RELEASES_URL) ?: return
            val updatedAt = extractField(json, "updated_at") ?: return

            if (UpdaterConfig.lastUpdatedAt.isEmpty()) {
                UpdaterConfig.save(updatedAt)
                return
            }
            if (updatedAt == UpdaterConfig.lastUpdatedAt) return

            val version = extractVersion(json) ?: "unknown"
            val downloadUrl = extractJarUrl(json) ?: return

            modMessage("§aNew version available: §e$version§a! Downloading...")
            downloadAndScheduleSwap(version, downloadUrl)
            UpdaterConfig.save(updatedAt)
            modMessage("§aUpdate ready! §eRestart Minecraft to apply v$version.")
        } catch (e: Exception) {
            logger.error("Auto-update check failed", e)
        }
    }

    private suspend fun downloadAndScheduleSwap(version: String, downloadUrl: String) = withContext(Dispatchers.IO) {
        val tempFile = downloadJar(downloadUrl, File(modsDir, "gobbyclient-$version.tmp"))
        val oldJars = findOldJars(version)
        registerSwapHook(tempFile, oldJars, version)
    }

    private fun downloadJar(url: String, dest: File): File {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "GobbyClient")
        FileOutputStream(dest).use { connection.inputStream.copyTo(it) }
        return dest
    }

    private fun findOldJars(newVersion: String): Array<File> =
        modsDir.listFiles { file ->
            file.name.startsWith("gobbyclient-") &&
                file.name.endsWith(".jar") &&
                file.name != "gobbyclient-$newVersion.jar"
        } ?: emptyArray()

    private fun registerSwapHook(tempFile: File, oldJars: Array<File>, version: String) {
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                val script = File(modsDir, "gobbyclient-update.bat")
                if (script.exists()) return@Thread

                script.writeText(buildUpdateScript(tempFile, oldJars, version))
                Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", "", script.absolutePath))
            } catch (e: Exception) {
                logger.warn("Failed to apply update on shutdown: ${e.message}")
            }
        })
    }

    private fun buildUpdateScript(tempFile: File, oldJars: Array<File>, version: String) = buildString {
        appendLine("@echo off")
        appendLine("timeout /t 2 >nul")
        appendLine("taskkill /F /IM javaw.exe >nul 2>&1")
        appendLine("taskkill /F /IM java.exe >nul 2>&1")
        appendLine("timeout /t 1 >nul")
        oldJars.forEach { appendLine("del \"${it.absolutePath}\"") }
        appendLine("move /Y \"${tempFile.absolutePath}\" \"${modsDir.absolutePath}\\gobbyclient-$version.jar\"")
        appendLine("echo Update complete!")
        appendLine("pause")
        appendLine("start /b \"\" cmd /c \"timeout /t 1 >nul & move /Y \"%~f0\" \"${tempBatDir.absolutePath}\\gobbyclient-update.bat\"\"")
        appendLine("exit")
    }

    private fun extractField(json: String, field: String): String? =
        Regex(""""$field"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)

    private fun extractVersion(json: String): String? =
        extractField(json, "name")?.let { VERSION_REGEX.find(it)?.groupValues?.get(1) }

    private fun extractJarUrl(json: String): String? =
        Regex(""""browser_download_url"\s*:\s*"([^"]+\.jar)"""").find(json)?.groupValues?.get(1)
}
