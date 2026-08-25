package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.LocationUtils
import gobby.utils.skyblockID

private const val SCREEN_TITLE = "Stats & Equipment"
private const val COMMAND = "stats"
private const val HELMET_SLOT = 11
private const val CHESTPLATE_SLOT = 20
private const val LEGGINGS_SLOT = 29
private const val BOOTS_SLOT = 38
private const val HOTBAR_CONTAINER_START = 81
private const val MAIN_INV_CONTAINER_START = 54
private const val HOTBAR_SIZE = 9
private const val LAST_INVENTORY_SLOT = 35
private const val ITEM_NOT_FOUND = -1

object EquipmentManager : ContainerSlotClicker(SCREEN_TITLE, COMMAND) {

    private var itemSlot = ITEM_NOT_FOUND

    val isSwapping: Boolean get() = isBusy

    fun swapHead(vararg skyblockIds: String) = swap(HELMET_SLOT, *skyblockIds)

    fun swapChestplate(vararg skyblockIds: String) = swap(CHESTPLATE_SLOT, *skyblockIds)

    fun swapLeggings(vararg skyblockIds: String) = swap(LEGGINGS_SLOT, *skyblockIds)

    fun swapBoots(vararg skyblockIds: String) = swap(BOOTS_SLOT, *skyblockIds)

    private fun swap(equipSlot: Int, vararg skyblockIds: String) = request { startSwap(equipSlot, *skyblockIds) }

    private fun startSwap(equipSlot: Int, vararg skyblockIds: String) {
        if (!LocationUtils.onSkyblock) return
        val found = findInInventory(*skyblockIds)
        if (found == ITEM_NOT_FOUND) return errorMessage("Item not found in inventory")
        itemSlot = found
        openFor(equipSlot)
    }

    override fun clickSlotFor(targetSlot: Int): Int =
        if (itemSlot < HOTBAR_SIZE) HOTBAR_CONTAINER_START + itemSlot
        else MAIN_INV_CONTAINER_START + (itemSlot - HOTBAR_SIZE)

    private fun findInInventory(vararg skyblockIds: String): Int {
        val inventory = mc.player?.inventory ?: return ITEM_NOT_FOUND
        return (0..LAST_INVENTORY_SLOT).firstOrNull { inventory.getItem(it).skyblockID in skyblockIds } ?: ITEM_NOT_FOUND
    }
}
