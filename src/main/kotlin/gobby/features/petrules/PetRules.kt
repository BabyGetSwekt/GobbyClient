package gobby.features.petrules

import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ConfigUtils
import gobby.utils.Utils.getRandomInt
import gobby.utils.managers.PETS_FOLDER
import gobby.utils.managers.PetEntry
import gobby.utils.managers.PetManager

private const val MIN_SWAP_DELAY = 3
private const val MAX_SWAP_DELAY = 7

data class PetRule(val category: String = "", val option: String = "", val petUuid: String = "")

data class PetRulesData(val rules: MutableList<PetRule> = mutableListOf())

object PetRules {

    private val config = ConfigUtils.makeConfig("rules", PETS_FOLDER) { PetRulesData() }

    private var pending: PetEntry? = null
    private var delay = 0

    val categories: List<TriggerCategory> = listOf(DungeonStart)

    val rules: List<PetRule> get() = config.data.rules

    fun add(rule: PetRule) = config.edit { this.rules.add(rule) }

    fun remove(rule: PetRule) = config.edit { this.rules.remove(rule) }

    fun petFor(rule: PetRule): PetEntry? = PetManager.pets.firstOrNull { it.uuid == rule.petUuid }

    fun categoryOf(rule: PetRule): TriggerCategory? = categories.firstOrNull { it.id == rule.category }

    fun labelOf(rule: PetRule): String = categoryOf(rule)?.optionById(rule.option)?.label ?: "Unknown trigger"

    fun fire(category: TriggerCategory, option: String) {
        val rule = rules.firstOrNull { it.category == category.id && it.option == option } ?: return
        val pet = petFor(rule) ?: return modMessage("A pet rule points at a pet you no longer own")
        if (PetManager.equipped?.uuid == pet.uuid) return
        pending = pet
        delay = getRandomInt(MIN_SWAP_DELAY, MAX_SWAP_DELAY)
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        val pet = pending ?: return
        if (--delay > 0) return
        pending = null
        PetManager.requestEquip(pet)
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        pending = null
        delay = 0
    }
}
