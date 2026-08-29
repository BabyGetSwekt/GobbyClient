package gobby.utils

import gobby.Gobbyclient.Companion.mc
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.network.HashedStack
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack

private const val NO_STATE_ID = 0
private const val LEFT_BUTTON = 0
const val MIDDLE_BUTTON = 2

object ContainerClicks {

    fun pickup(syncId: Int, slot: Int) {
        mc.connection?.send(
            ServerboundContainerClickPacket(
                syncId, NO_STATE_ID, slot.toShort(), LEFT_BUTTON.toByte(), ContainerInput.PICKUP,
                Int2ObjectOpenHashMap<HashedStack>(), HashedStack.EMPTY
            )
        )
    }

    fun close(syncId: Int) {
        mc.connection?.send(ServerboundContainerClosePacket(syncId))
    }

    fun input(syncId: Int, slot: Int, button: Int = MIDDLE_BUTTON, action: ContainerInput = ContainerInput.CLONE) {
        val player = mc.player ?: return
        mc.gameMode?.handleContainerInput(syncId, slot, button, action, player)
    }

    fun quickMove(syncId: Int, slot: Int) = input(syncId, slot, LEFT_BUTTON, ContainerInput.QUICK_MOVE)

    fun clone(menu: AbstractContainerMenu, slot: Int) {
        val connection = mc.connection ?: return
        val player = mc.player ?: return
        val before = menu.slots.map { it.item.copy() }
        menu.clicked(slot, LEFT_BUTTON, ContainerInput.CLONE, player)
        val changed = Int2ObjectOpenHashMap<HashedStack>()
        before.indices
            .filterNot { ItemStack.matches(before[it], menu.slots[it].item) }
            .forEach { changed.put(it, HashedStack.create(menu.slots[it].item, connection.decoratedHashOpsGenenerator())) }
        connection.send(
            ServerboundContainerClickPacket(
                menu.containerId, menu.stateId, slot.toShort(), LEFT_BUTTON.toByte(), ContainerInput.CLONE,
                changed, HashedStack.create(menu.carried, connection.decoratedHashOpsGenenerator())
            )
        )
    }
}
