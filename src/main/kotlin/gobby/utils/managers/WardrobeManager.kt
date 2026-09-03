package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ArmorUpdateEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.gui.GuiOpenEvent
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.ConfigUtils
import gobby.utils.getLoreStrings
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack

private val SCREEN_TITLE = Regex("""^\(\d+/\d+\) Armor Sets$""")
private const val COMMAND = "wardrobe"
private const val FIRST_WARDROBE_SLOT = 36
private const val WARDROBE_SLOT_INDEX_OFFSET = 1

data class WardrobeData(
    var equippedSlot: String = "",
    var helmet: String = "",
    var chestplate: String = "",
    var leggings: String = "",
    var boots: String = ""
)

object WardrobeManager : ContainerSlotClicker(SCREEN_TITLE, COMMAND) {

    private val config = ConfigUtils.makeConfig("wardrobe", "wardrobe") { WardrobeData() }

    private var requestedSlot = ""

    private var equippedSlot: String
        get() = config.data.equippedSlot
        set(value) = config.edit { equippedSlot = value }

    val wornHelmet: String get() = config.data.helmet

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
    fun onArmorUpdate(event: ArmorUpdateEvent) = config.edit {
        when (event.slot) {
            EquipmentSlot.HEAD -> helmet = event.uuidAfter
            EquipmentSlot.CHEST -> chestplate = event.uuidAfter
            EquipmentSlot.LEGS -> leggings = event.uuidAfter
            else -> boots = event.uuidAfter
        }
    }

    @SubscribeEvent
    fun onWardrobeScreenOpened(event: GuiOpenEvent) {
        val screen = event.screen as? AbstractContainerScreen<*> ?: return
        if (SCREEN_TITLE.containsMatchIn(screen.title.string)) equippedSlot = ""
    }

    @SubscribeEvent
    fun onWardrobeWorldLoad(event: WorldLoadEvent) {
        equippedSlot = ""
    }
}
