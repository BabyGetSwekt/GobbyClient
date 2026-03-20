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
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket
import net.minecraft.screen.ScreenHandlerType

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
        if (pendingSyncId != -1 && mc.currentScreen == null) {
            pendingSyncId = -1
        }
    }

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (mc.player == null || mc.world == null) return
        if (!inDungeons || inBoss || !enabled) return

        when (val packet = event.packet) {
            is OpenScreenS2CPacket -> {
                if (!packet.name.string.equalsOneOf("Chest", "Large Chest", "")) return
                pendingSyncId = packet.syncId
                chestSize = if (packet.screenHandlerType == ScreenHandlerType.GENERIC_9X6) ChestSize.LARGE else ChestSize.SMALL
                secretsFound = 0
                hasNonEmptyOther = false
                event.cancel()
            }

            is ScreenHandlerSlotUpdateS2CPacket -> {
                if (pendingSyncId == -1) return
                val slot = packet.slot
                if (slot < 0 || slot > chestSize.lastSlot) return

                if (slot in chestSize.secretSlots) {
                    if (packet.stack.isSecret()) secretsFound++
                } else if (!packet.stack.isEmpty) {
                    hasNonEmptyOther = true
                }

                if (slot == chestSize.lastSlot) {
                    if (secretsFound == chestSize.secretSlots.size && !hasNonEmptyOther) {
                        mc.networkHandler?.sendPacket(CloseHandledScreenC2SPacket(pendingSyncId))
                    }
                    pendingSyncId = -1
                }
            }
        }
    }
}
