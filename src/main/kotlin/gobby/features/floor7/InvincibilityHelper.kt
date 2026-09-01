package gobby.features.floor7

import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.RefreshSetting
import gobby.gui.click.TextSetting
import gobby.utils.BONZO_MASK_IDS
import gobby.utils.SPIRIT_MASK_IDS
import gobby.utils.managers.EquipmentManager
import gobby.utils.managers.InvincibilityManager
import gobby.utils.managers.PetEntry
import gobby.utils.managers.PetManager
import gobby.utils.skyblock.dungeon.DungeonUtils.getPhase
import gobby.utils.timer.Clock

object InvincibilityHelper : Module("Invincibility Helper",
    "Swaps to your next death save after one pops in P3. Automatically puts on your previous pet after your phoenix popped.",
    Category.FLOOR7
) {

    private val phoenixStatus by TextSetting("Phoenix", desc = "The Phoenix pet this module will summon") { phoenixLabel() }
    private val refreshPets by RefreshSetting("Refresh Pets", desc = "Rescans your pets menu", busy = { PetManager.isScanning }) { PetManager.scan() }

    private const val SWAP_DELAY_MS = 1000L
    private const val PHOENIX_NAME = "Phoenix"

    private enum class Save(val onCooldown: () -> Boolean, val masks: Set<String>?) {
        BONZO({ InvincibilityManager.isBonzoOnCooldown }, BONZO_MASK_IDS),
        SPIRIT({ InvincibilityManager.isSpiritOnCooldown }, SPIRIT_MASK_IDS),
        PHOENIX({ InvincibilityManager.isPhoenixOnCooldown }, null)
    }

    private val swapClock = Clock()
    private val onCooldown = mutableMapOf<Save, Boolean>()
    private var poppedSave: Save? = null
    private var petBeforePhoenix: PetEntry? = null

    private fun phoenixPet(): PetEntry? = PetManager.pets.firstOrNull { it.name.equals(PHOENIX_NAME, true) }

    private fun phoenixLabel(): String = phoenixPet()?.let { "${it.label} detect!" } ?: "No phoenix found, click refresh"

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!enabled) return
        trackPops()
        val popped = poppedSave ?: return
        if (!swapClock.hasTimePassed(SWAP_DELAY_MS)) return
        poppedSave = null
        if (getPhase() != 3) return
        if (popped == Save.PHOENIX) restorePet() else equipNextSave()
    }

    private fun trackPops() {
        Save.entries.forEach { save ->
            val active = save.onCooldown()
            if (active && onCooldown[save] != true && getPhase() == 3) schedulePop(save)
            onCooldown[save] = active
        }
    }

    private fun schedulePop(save: Save) {
        poppedSave = save
        swapClock.update()
    }

    private fun equipNextSave() {
        val mask = availableMask() ?: return equipPhoenix()
        EquipmentManager.swapHead(*mask.toTypedArray())
    }

    private fun availableMask(): Set<String>? = Save.entries
        .mapNotNull { save -> save.masks?.takeIf { !save.onCooldown() } }
        .firstOrNull { EquipmentManager.hasInInventory(*it.toTypedArray()) }

    private fun equipPhoenix() {
        if (Save.PHOENIX.onCooldown()) return
        val pet = phoenixPet() ?: return
        if (PetManager.equipped?.uuid == pet.uuid) return
        petBeforePhoenix = PetManager.equipped
        PetManager.requestEquip(pet, announce = false)
    }

    private fun restorePet() {
        val pet = petBeforePhoenix ?: return
        petBeforePhoenix = null
        PetManager.requestEquip(pet, announce = false)
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        onCooldown.clear()
        poppedSave = null
        petBeforePhoenix = null
    }
}
