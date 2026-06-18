package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.Utils.equalsOneOf
import gobby.utils.skyblock.dungeon.DungeonUtils.isSecret
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.inventory.MenuType

object AutoCloseChest : Module("Auto Close Chest", "Automatically closes secret chests in dungeons", Category.DUNGEONS) {

    private var pendingSyncId = -1
    private var chestSize = ChestSize.SMALL
    private var secretsFound = 0
    private var hasNonEmptyOther = false

    private enum class ChestSize(val lastSlot: Int, val secretSlots: Set<Int>) {
        SMALL(26, setOf(13)),
        LARGE(53, setOf(13, 40))
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        //? if >26.1.2
        if (pendingSyncId != -1 && mc.gui.screen() == null) {
        //? if <=26.1.2
        /*if (pendingSyncId != -1 && mc.screen == null) {*/
            pendingSyncId = -1
        }
    }

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (mc.player == null || mc.level == null) return
        if (!inDungeons || inBoss || !enabled) return

        when (val packet = event.packet) {
            is ClientboundOpenScreenPacket -> {
                if (!packet.title.string.equalsOneOf("Chest", "Large Chest", "")) return
                pendingSyncId = packet.containerId
                chestSize = if (packet.type == MenuType.GENERIC_9x6) ChestSize.LARGE else ChestSize.SMALL
                secretsFound = 0
                hasNonEmptyOther = false
                event.cancel()
            }

            is ClientboundContainerSetSlotPacket -> {
                if (pendingSyncId == -1) return
                val slot = packet.slot
                if (slot < 0 || slot > chestSize.lastSlot) return

                if (slot in chestSize.secretSlots) {
                    if (packet.item.isSecret()) secretsFound++
                } else if (!packet.item.isEmpty) {
                    hasNonEmptyOther = true
                }

                if (slot == chestSize.lastSlot) {
                    if (secretsFound == chestSize.secretSlots.size && !hasNonEmptyOther) {
                        mc.connection?.send(ServerboundContainerClosePacket(pendingSyncId))
                    }
                    pendingSyncId = -1
                }
            }
        }
    }
}
