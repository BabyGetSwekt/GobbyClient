package gobby.features.skyblock

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import gobby.Gobbyclient.Companion.logger
import net.fabricmc.loader.api.ModContainer
import net.fabricmc.loader.impl.FabricLoaderImpl
import java.io.File

object ModIdHider {

    private val configFile = File("./config/gobbyclientFabric/hidden_mods.json")
    private val gson = Gson()
    private val hiddenMods = mutableListOf<String>()
    private val stringListType = object : TypeToken<List<String>>() {}.type

    init {
        load()
    }

    @JvmStatic
    fun getHiddenMods(): List<String> = hiddenMods.toList()

    fun addMod(id: String) {
        val trimmed = id.trim().lowercase()
        if (trimmed.isNotEmpty() && trimmed !in hiddenMods) {
            hiddenMods.add(trimmed)
        }
    }

    fun removeMod(id: String) {
        hiddenMods.remove(id)
    }

    fun replaceAll(ids: List<String>) {
        hiddenMods.clear()
        ids.forEach { addMod(it) }
    }

    fun save() {
        configFile.parentFile.mkdirs()
        configFile.writeText(gson.toJson(hiddenMods))
        applyToLoader()
    }

    fun load() {
        hiddenMods.clear()
        if (configFile.exists()) {
            try {
                val loaded: List<String> = gson.fromJson(configFile.readText(), stringListType)
                hiddenMods.addAll(loaded)
            } catch (_: Exception) {
                addDefaults()
            }
        } else {
            addDefaults()
            save()
        }
    }

    /**
     * Uses reflection to remove hidden mods from FabricLoaderImpl's internal mods list.
     * This is needed because the mixin on FabricLoaderImpl doesn't fire.
     * Knot loads the class before mod mixins are registered.
     */
    fun applyToLoader() {
        try {
            val loader = FabricLoaderImpl.INSTANCE
            val modsField = loader.javaClass.getDeclaredField("mods")
            modsField.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val mods = modsField.get(loader) as MutableList<ModContainer>
            val removed = mods.removeAll { it.metadata.id in hiddenMods }

            if (removed) {
                logger.info("Successfully hid mods from loader")
            }
        } catch (e: Exception) {
            logger.error("Failed to hide mods from loader", e)
        }
    }

    private fun addDefaults() {
        hiddenMods.add("gobbyclient")
        hiddenMods.add("devoniandoogan") // shoutout to devoniandoogan
    }
}
