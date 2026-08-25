package gobby.features.skyblock

import gobby.Gobbyclient.Companion.logger
import net.fabricmc.loader.api.ModContainer
import net.fabricmc.loader.impl.FabricLoaderImpl
import gobby.utils.ConfigUtils

object ModIdHider {

    private val config = ConfigUtils.makeConfig("hidden_mods") { mutableListOf<String>() }
    private val modIds get() = config.data

    init {
        if (config.isNew) {
            addDefaults()
            save()
        }
    }

    @JvmStatic
    fun getHiddenMods(): List<String> = modIds.toList()

    fun addMod(id: String) {
        val trimmed = id.trim().lowercase()
        if (trimmed.isNotEmpty() && trimmed !in modIds) {
            modIds.add(trimmed)
        }
    }

    fun removeMod(id: String) {
        modIds.remove(id)
    }

    fun replaceAll(ids: List<String>) {
        modIds.clear()
        ids.forEach { addMod(it) }
    }

    fun save() {
        config.save()
        applyToLoader()
    }

    fun load() {
        config.reload()
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
            val removed = mods.removeAll { it.metadata.id in modIds }

            if (removed) {
                logger.info("Successfully hid mods from loader")
            }
        } catch (e: Exception) {
            logger.error("Failed to hide mods from loader", e)
        }
    }

    private fun addDefaults() {
        modIds.add("gobbyclient")
        modIds.add("devoniandoogan") // shoutout to devoniandoogan
    }
}
