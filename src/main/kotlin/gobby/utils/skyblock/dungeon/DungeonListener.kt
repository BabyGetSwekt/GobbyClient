package gobby.utils.skyblock.dungeon

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldUnloadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonClass
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonTeammate
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.Formatting

object DungeonListener {

    private val teammateRegex = Regex("""\[(\d+)]\s+(\w+)\s+(?:(.)\s+)?\((\w+)\s+([IVXLC]+)\)""")

    val teammates = mutableMapOf<String, DungeonTeammate>()
    var doorOpener = ""
        private set
    var isBloodOpened = false
        private set

    private var leapTicks = 0
    private const val LEAP_TIMEOUT_TICKS = 40

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (!inDungeons) return
        if (event.packet !is PlayerListS2CPacket) return

        val tabEntries = mc.networkHandler?.playerList ?: return
        updateDungeonTeammates(tabEntries)
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!inDungeons || inBoss) return
        val message = event.message

        if (message == "The BLOOD DOOR has been opened!") {
            isBloodOpened = true
            doorOpener = ""
            return
        }

        val opener = message.substringBefore(" opened a WITHER door!")
        if ("$opener opened a WITHER door!" != message) return
        if (opener !in teammates) return
        doorOpener = opener
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (inBoss && doorOpener.isNotEmpty()) doorOpener = ""

        val target = DungeonUtils.leapTarget ?: run {
            leapTicks = 0
            return
        }

        if (++leapTicks > LEAP_TIMEOUT_TICKS) {
            DungeonUtils.leapTarget = null
            leapTicks = 0
            return
        }

        val screen = mc.currentScreen as? GenericContainerScreen ?: return
        if (!screen.title.string.contains("Spirit Leap")) return
        val handler = screen.screenHandler

        for (slotIndex in 10..18) {
            val slot = handler.slots.getOrNull(slotIndex) ?: continue
            val stack = slot.stack ?: continue
            if (stack.isEmpty) continue
            val itemName = Formatting.strip(stack.name.string) ?: continue
            if (itemName.equals(target, ignoreCase = true)) {
                mc.interactionManager?.clickSlot(
                    handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player
                )
                DungeonUtils.leapTarget = null
                leapTicks = 0
                return
            }
        }
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldUnloadEvent) {
        teammates.clear()
        doorOpener = ""
        isBloodOpened = false
        DungeonUtils.leapTarget = null
    }

    private fun updateDungeonTeammates(tabList: Collection<PlayerListEntry>) {
        for (entry in tabList) {
            val displayText = entry.displayName ?: continue
            val line = Formatting.strip(displayText.string) ?: continue
            if (line.isBlank()) continue

            val match = teammateRegex.find(line) ?: continue
            val (levelStr, name, emblem, className, classLevel) = match.destructured
            val dungeonClass = DungeonClass.entries.firstOrNull { it.name.equals(className, ignoreCase = true) }
                ?: DungeonClass.Unknown

            teammates[name] = DungeonTeammate(
                name = name,
                dungeonClass = dungeonClass,
                classLevel = classLevel,
                playerLevel = levelStr.toIntOrNull() ?: 0,
                emblem = emblem.ifEmpty { null }
            )
        }
    }
}
