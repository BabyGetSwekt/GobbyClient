package gobby.features.petrules

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ConfigUtils
import gobby.utils.Utils.swapDelayTicks
import gobby.utils.managers.PETS_FOLDER
import gobby.utils.managers.PetEntry
import gobby.utils.managers.PetManager

data class PetRule(val category: String = "", val option: String = "", val petUuid: String = "", var enabled: Boolean = true)

data class PetRulesData(val rules: MutableList<PetRule> = mutableListOf())

object PetRules {

    private val config = ConfigUtils.makeConfig("rules", PETS_FOLDER) { PetRulesData() }

    private var pending: PetEntry? = null
    private var delay = 0
    private var attempts = 0

    val categories: List<TriggerCategory> = listOf(DungeonStart, BossSpawn)

    val rules: List<PetRule> get() = config.data.rules

    fun add(rule: PetRule) = config.edit { this.rules.add(rule) }

    fun remove(rule: PetRule) = config.edit { this.rules.remove(rule) }

    fun toggle(rule: PetRule) = config.edit { rule.enabled = !rule.enabled }

    fun petFor(rule: PetRule): PetEntry? = PetManager.pets.firstOrNull { it.uuid == rule.petUuid }

    fun categoryOf(rule: PetRule): TriggerCategory? = categories.firstOrNull { it.id == rule.category }

    fun labelOf(rule: PetRule): String = categoryOf(rule)?.optionById(rule.option)?.label ?: "Unknown trigger"

    fun fire(category: TriggerCategory, option: String): Boolean {
        val rule = rules.firstOrNull { it.category == category.id && it.option == option && it.enabled } ?: return false
        val pet = petFor(rule) ?: return true.also { modMessage("A pet rule points at a pet you no longer own") }
        if (pending != null) return true
        if (PetManager.equipped?.uuid == pet.uuid) return true
        pending = pet
        delay = swapDelayTicks()
        attempts = 0
        return true
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        val pet = pending ?: return
        if (delay > 0) {
            delay--
            return
        }
        if (PetManager.equipped?.uuid == pet.uuid) return clearPending()
        if (PetManager.isSwapping || mc.gui.screen() != null) return
        if (attempts >= 3) {
            clearPending()
            return errorMessage("Could not swap to ${pet.label}")
        }
        attempts++
        delay = swapDelayTicks()
        PetManager.requestEquip(pet, announce = false)
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) = clearPending()

    private fun clearPending() {
        pending = null
        delay = 0
        attempts = 0
    }
}
