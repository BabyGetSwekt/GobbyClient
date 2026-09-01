package gobby.utils.managers

import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.gui.GuiOpenEvent
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.ConfigUtils
import gobby.utils.getLoreStrings
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

private val SCREEN_TITLE = Regex("""^\(\d+/\d+\) Armor Sets$""")
private const val COMMAND = "wardrobe"

data class WardrobeData(var equippedSlot: Int = 0)

object WardrobeManager : ContainerSlotClicker(SCREEN_TITLE, COMMAND) {

    private val config = ConfigUtils.makeConfig("wardrobe", "wardrobe") { WardrobeData() }

    private var requestedSlot = 0

    private var equippedSlot: Int
        get() = config.data.equippedSlot
        set(value) = config.edit { equippedSlot = value }

    val isSwapping: Boolean get() = isBusy

    fun swap(wardrobeSlot: Int) {
        if (wardrobeSlot == equippedSlot) return errorMessage("Current set already equipped")
        request {
            requestedSlot = wardrobeSlot
            openFor(36 + wardrobeSlot - 1)
        }
    }

    override fun shouldClick(stack: ItemStack): Boolean {
        equippedSlot = requestedSlot
        if (!stack.isEquippedSet) return true
        errorMessage("Current set already equipped")
        return false
    }

    private val ItemStack.isEquippedSet: Boolean
        get() = hoverName.string.noControlCodes.contains("Equipped", true) ||
            getLoreStrings().any { it.noControlCodes.contains("Click to unequip", true) }

    @SubscribeEvent
    fun onWardrobeScreenOpened(event: GuiOpenEvent) {
        val screen = event.screen as? AbstractContainerScreen<*> ?: return
        if (SCREEN_TITLE.containsMatchIn(screen.title.string)) equippedSlot = 0
    }

    @SubscribeEvent
    fun onWardrobeWorldLoad(event: WorldLoadEvent) {
        equippedSlot = 0
    }
}
