package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.LocationUtils
import gobby.utils.getItemUUID
import gobby.utils.skyblockID
import net.minecraft.world.item.ItemStack

private val SCREEN_TITLE = Regex("Stats & Equipment")
private const val COMMAND = "stats"

enum class ArmorPiece(val equipSlot: Int, val label: String) {
    HELMET(11, "helmet"),
    CHESTPLATE(20, "chestplate"),
    LEGGINGS(29, "leggings"),
    BOOTS(38, "boots")
}

object EquipmentManager : ContainerSlotClicker(SCREEN_TITLE, COMMAND) {

    private var itemSlot = -1
    private var delayTicks = 0
    private var delayedSwap: (() -> Unit)? = null

    val isSwapping: Boolean get() = isBusy

    fun hasInInventory(vararg skyblockIds: String): Boolean = findInInventory(matching(*skyblockIds)) != -1

    fun swap(piece: ArmorPiece, vararg skyblockIds: String, delayTicks: Int = 0) =
        schedule(delayTicks) { startSwap(piece, matching(*skyblockIds)) }

    fun swapByUuid(piece: ArmorPiece, uuid: String, delayTicks: Int = 0) = schedule(delayTicks) {
        if (uuid.isEmpty()) errorMessage("No previous ${piece.label} stored")
        else startSwap(piece) { it.getItemUUID == uuid }
    }

    private fun schedule(delayTicks: Int, action: () -> Unit) {
        if (delayTicks <= 0) return request(action)
        this.delayTicks = delayTicks
        delayedSwap = action
    }

    @SubscribeEvent
    fun onDelayTick(event: ClientTickEvent.Post) {
        if (delayTicks <= 0 || --delayTicks > 0) return
        delayedSwap?.let(::request)
        delayedSwap = null
    }

    @SubscribeEvent
    fun onDelayWorldLoad(event: WorldLoadEvent) {
        delayTicks = 0
        delayedSwap = null
    }

    private fun matching(vararg skyblockIds: String): (ItemStack) -> Boolean = { it.skyblockID in skyblockIds }

    private fun startSwap(piece: ArmorPiece, match: (ItemStack) -> Boolean) {
        if (!LocationUtils.onSkyblock) return
        val found = findInInventory(match)
        if (found == -1) return errorMessage("Item not found in inventory")
        itemSlot = found
        openFor(piece.equipSlot)
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
