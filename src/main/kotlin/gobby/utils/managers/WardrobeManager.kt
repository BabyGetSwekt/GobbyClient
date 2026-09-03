package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ArmorUpdateEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.gui.GuiOpenEvent
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.getLoreStrings
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack

private val SCREEN_TITLE = Regex("""^\(\d+/\d+\) Armor Sets$""")
private const val COMMAND = "wardrobe"
private const val FIRST_WARDROBE_SLOT = 36
private const val WARDROBE_SLOT_INDEX_OFFSET = 1

object WardrobeManager : ContainerSlotClicker(SCREEN_TITLE, COMMAND) {

    private var requestedSlot = ""

    private var equippedSlot: String
        get() = PlayerInfo.equippedWardrobeSlot
        set(value) { PlayerInfo.equippedWardrobeSlot = value }

    val wornHelmet: String get() = PlayerInfo.helmet

    val isSwapping: Boolean get() = isBusy

    fun isWardrobeScreenOpen(): Boolean {
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return false
        return SCREEN_TITLE.containsMatchIn(screen.title.string)
    }

    fun swap(wardrobeSlot: Int) {
        val wardrobeOpen = isWardrobeScreenOpen()
        if (!wardrobeOpen && wardrobeSlot.toString() == equippedSlot) {
            closeOpenScreen()
            return errorMessage("Current set already equipped")
        }
        val slot = FIRST_WARDROBE_SLOT + wardrobeSlot - WARDROBE_SLOT_INDEX_OFFSET
        if (wardrobeOpen) {
            requestedSlot = wardrobeSlot.toString()
            clickOpenScreen(slot)
            return
        }
        request {
            requestedSlot = wardrobeSlot.toString()
            openFor(slot)
        }
    }

    override fun shouldClick(stack: ItemStack): Boolean {
        if (stack.isEquippedSet) {
            errorMessage("Current set already equipped")
            return false
        }
        equippedSlot = requestedSlot
        return true
    }

    private val ItemStack.isEquippedSet: Boolean
        get() = hoverName.string.noControlCodes.contains("Equipped", true) ||
            getLoreStrings().any { it.noControlCodes.contains("Click to unequip", true) }

    @SubscribeEvent
    fun onArmorUpdate(event: ArmorUpdateEvent) {
        when (event.slot) {
            EquipmentSlot.HEAD -> PlayerInfo.updateArmor("helmet", event.uuidAfter)
            EquipmentSlot.CHEST -> PlayerInfo.updateArmor("chestplate", event.uuidAfter)
            EquipmentSlot.LEGS -> PlayerInfo.updateArmor("leggings", event.uuidAfter)
            else -> PlayerInfo.updateArmor("boots", event.uuidAfter)
        }
    }

    @SubscribeEvent
    fun onWardrobeScreenOpened(event: GuiOpenEvent) {
        val screen = event.screen as? AbstractContainerScreen<*> ?: return
        if (SCREEN_TITLE.containsMatchIn(screen.title.string)) equippedSlot = ""
    }

    @SubscribeEvent
    fun onWardrobeWorldLoad(event: WorldLoadEvent) {
        PlayerInfo.clearActiveSets()
    }
}
