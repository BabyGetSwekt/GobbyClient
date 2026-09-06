package gobby.utils.managers

import gobby.Gobbyclient
import gobby.Gobbyclient.Companion.mc
import gobby.events.ArmorUpdateEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.getItemUUID
import gobby.utils.skyblockID
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack

object ArmorTracker {

    private val ARMOR_SLOTS = mapOf(
        5 to EquipmentSlot.HEAD,
        6 to EquipmentSlot.CHEST,
        7 to EquipmentSlot.LEGS,
        8 to EquipmentSlot.FEET
    )

    private data class Worn(val uuid: String, val id: String)

    private val worn = mutableMapOf<EquipmentSlot, Worn>()

    fun uuidOf(slot: EquipmentSlot): String = worn[slot]?.uuid ?: ""

    fun idOf(slot: EquipmentSlot): String = worn[slot]?.id ?: ""

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        when (val packet = event.packet) {
            is ClientboundContainerSetSlotPacket -> if (packet.containerId == 0) mc.execute { update(packet.slot, packet.item) }
            is ClientboundContainerSetContentPacket -> if (packet.containerId == 0) mc.execute { syncAll(packet.items) }
        }
    }

    private fun syncAll(items: List<ItemStack>) =
        ARMOR_SLOTS.keys.forEach { update(it, items.getOrNull(it) ?: ItemStack.EMPTY) }

    private fun update(menuSlot: Int, stack: ItemStack) {
        val slot = ARMOR_SLOTS[menuSlot] ?: return
        val after = stack.getItemUUID ?: ""
        if (after.isEmpty() && !stack.isEmpty) return
        val before = uuidOf(slot)
        worn[slot] = Worn(after, stack.skyblockID)
        if (after == before) return
        Gobbyclient.EVENT_MANAGER.publish(ArmorUpdateEvent(slot, before, after))
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) = worn.clear()
}
