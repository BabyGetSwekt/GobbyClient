package gobby.features.skyblock

import gobby.Gobbyclient.Companion.logger
import net.fabricmc.loader.api.ModContainer
import net.fabricmc.loader.impl.FabricLoaderImpl
import gobby.utils.ConfigUtils

object ModIdHider {

    private val config = ConfigUtils.makeConfig("hidden_mods") { mutableListOf<String>() }
    private val modIds get() = config.data

    init {
        if (config.isNew) addDefaults()
        val stored = ModIdRules.storable(modIds)
        if (stored != modIds.toList()) {
            modIds.clear()
            modIds.addAll(stored)
            save()
        }
    }

    @JvmStatic
    fun getHiddenMods(): List<String> = ModIdRules.effective(modIds)

    fun addMod(id: String) {
        val trimmed = ModIdRules.clean(id)
        if (trimmed.isNotEmpty() && !ModIdRules.isProtected(trimmed) && trimmed !in modIds) {
            modIds.add(trimmed)
        }
    }

    fun removeMod(id: String) {
        if (ModIdRules.isProtected(id)) return
        modIds.remove(ModIdRules.clean(id))
    }

    fun replaceAll(ids: List<String>) {
        val kept = ModIdRules.storable(ids)
        modIds.clear()
        modIds.addAll(kept)
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
            val hidden = getHiddenMods()
            val removed = mods.removeAll { it.metadata.id in hidden }

            if (removed) {
                logger.info("Successfully hid mods from loader")
            }
        } catch (e: Exception) {
            logger.error("Failed to hide mods from loader", e)
        }
    }

    private fun addDefaults() {
        modIds.add("devoniandoogan") // shoutout to devoniandoogan
    }
}
