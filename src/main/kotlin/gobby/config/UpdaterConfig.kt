package gobby.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File

object UpdaterConfig {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    val updatesFolder: File = FabricLoader.getInstance().configDir.resolve("gobbyclientFabric/updates").toFile().also { it.mkdirs() }
    private val configFile = File(updatesFolder, "updater.json")

    var lastUpdatedAt: String = ""
        private set

    init {
        load()
    }

    private fun load() {
        if (!configFile.exists()) return
        try {
            val data = gson.fromJson(configFile.readText(), Data::class.java) ?: return
            lastUpdatedAt = data.lastUpdatedAt
        } catch (_: Exception) {}
    }

    fun save(updatedAt: String) {
        lastUpdatedAt = updatedAt
        try {
            configFile.writeText(gson.toJson(Data(lastUpdatedAt)))
        } catch (_: Exception) {}
    }

    private data class Data(val lastUpdatedAt: String = "")
}
