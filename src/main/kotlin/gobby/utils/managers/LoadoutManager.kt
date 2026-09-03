package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.gui.GuiOpenEvent
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.getLoreStrings
import gobby.utils.itemPath
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

private val SCREEN_TITLE = Regex("""^\(\d+/\d+\) Loadouts$""")
private const val COMMAND = "loadout"
private val LOADOUT_SLOTS = listOf(14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43)

object LoadoutManager : ContainerSlotClicker(SCREEN_TITLE, COMMAND) {
    private var requestedSlot = ""

    val isSwapping: Boolean get() = isBusy

    private var equippedSlot: String
        get() = PlayerInfo.equippedLoadoutSlot
        set(value) { PlayerInfo.equippedLoadoutSlot = value }

    val isLoadoutScreenOpen: Boolean
        get() = (mc.gui.screen() as? AbstractContainerScreen<*>)?.let {
            SCREEN_TITLE.containsMatchIn(it.title.string)
        } == true

    fun swap(loadoutSlot: Int) {
        val screenOpen = isLoadoutScreenOpen
        if (!screenOpen && loadoutSlot.toString() == equippedSlot) {
            closeOpenScreen()
            return errorMessage("Current loadout already equipped")
        }
        val slot = LOADOUT_SLOTS.getOrNull(loadoutSlot - 1) ?: return
        if (screenOpen) {
            requestedSlot = loadoutSlot.toString()
            clickOpenScreen(slot)
            return
        }
        request {
            requestedSlot = loadoutSlot.toString()
            openFor(slot)
        }
    }

    override fun shouldClick(stack: ItemStack): Boolean {
        if (stack.isLockedLoadout) {
            errorMessage("Loadout slot is locked")
            return false
        }
        val canEquip = !stack.isEmpty && stack.getLoreStrings().any {
            it.noControlCodes.contains("Left-click to equip!", true)
        }
        if (!canEquip) {
            errorMessage("Current loadout already equipped")
            return false
        }
        equippedSlot = requestedSlot
        return true
    }

    private val ItemStack.isLockedLoadout: Boolean
        get() = itemPath == "red_dye" || hoverName.string.noControlCodes.contains("Locked", true)

    @SubscribeEvent
    fun onLoadoutScreenOpened(event: GuiOpenEvent) {
        val screen = event.screen as? AbstractContainerScreen<*> ?: return
        if (SCREEN_TITLE.containsMatchIn(screen.title.string)) equippedSlot = ""
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) = PlayerInfo.clearActiveSets()
}
