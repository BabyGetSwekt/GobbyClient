package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.LocationUtils
import gobby.utils.getItemUUID
import gobby.utils.skyblockID
import net.minecraft.world.item.ItemStack

private val SCREEN_TITLE = Regex("Stats & Equipment")
private const val COMMAND = "stats"

object EquipmentManager : ContainerSlotClicker(SCREEN_TITLE, COMMAND) {

    private var itemSlot = -1

    val isSwapping: Boolean get() = isBusy

    fun hasInInventory(vararg skyblockIds: String): Boolean = findInInventory(matching(*skyblockIds)) != -1

    fun swapHead(vararg skyblockIds: String) = swap(11, matching(*skyblockIds))

    fun swapChestplate(vararg skyblockIds: String) = swap(20, matching(*skyblockIds))

    fun swapLeggings(vararg skyblockIds: String) = swap(29, matching(*skyblockIds))

    fun swapBoots(vararg skyblockIds: String) = swap(38, matching(*skyblockIds))

    fun swapHeadByUuid(uuid: String) {
        if (uuid.isEmpty()) return errorMessage("No previous helmet stored")
        swap(11) { it.getItemUUID == uuid }
    }

    private fun matching(vararg skyblockIds: String): (ItemStack) -> Boolean = { it.skyblockID in skyblockIds }

    private fun swap(equipSlot: Int, match: (ItemStack) -> Boolean) = request { startSwap(equipSlot, match) }

    private fun startSwap(equipSlot: Int, match: (ItemStack) -> Boolean) {
        if (!LocationUtils.onSkyblock) return
        val found = findInInventory(match)
        if (found == -1) return errorMessage("Item not found in inventory")
        itemSlot = found
        openFor(equipSlot)
    }

    override fun sendClick(syncId: Int, slot: Int) {
        announceSwap()
        super.sendClick(syncId, slot)
    }

    private fun announceSwap() {
        val name = mc.player?.inventory?.getItem(itemSlot)?.hoverName?.string?.noControlCodes ?: return
        modMessage("§aSwapped to §6$name§a!")
    }

    override fun clickSlotFor(targetSlot: Int): Int =
        if (itemSlot < 9) 81 + itemSlot else 54 + (itemSlot - 9)

    private fun findInInventory(match: (ItemStack) -> Boolean): Int {
        val inventory = mc.player?.inventory ?: return -1
        return (0..35).firstOrNull { match(inventory.getItem(it)) } ?: -1
    }
}
