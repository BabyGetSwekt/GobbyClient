package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.LocationUtils
import gobby.utils.managers.PacketOrderManager
import gobby.utils.timer.Clock
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.inventory.ChestMenu
import gobby.utils.ContainerClicks

object AutoExperiments : Module("Auto Experiments", "Automatically does experiments", Category.SKYBLOCK) {

    private val delayMs by NumberSetting("Click Delay", 200, 0, 1000, 10, desc = "Ms between clicks")
    private val serumCount by NumberSetting("Serum Count", 0, 0, 3, 1, desc = "Consumed Metaphysical Serum count")
    private val maxXp by BooleanSetting("Get Max XP", false, desc = "Solve to 15 (Chrono) / 20 (Ultra) for max XP")

    private const val PIVOT_SLOT = 49
    private const val ULTRA_FILL_SLOT = 44
    private val CHRONO_SLOTS = 10..43
    private val ULTRA_SLOTS = 9..44
    private val NUMERIC_NAME = Regex("\\d+")

    private enum class Mode { CHRONO, ULTRA }

    private var sequence: List<Int> = emptyList()
    private var sentCount = 0
    private var sequenceCaptured = false
    private var lastCapturedSlot = -1
    private var mode: Mode? = null
    private val packetClock = Clock()

    private val onPrivateIsland get() = LocationUtils.location == "Private Island"
    private val chronoCap get() = if (maxXp) 15 else 11 - serumCount
    private val ultraCap get() = if (maxXp) 20 else 9 - serumCount

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (!enabled || !onPrivateIsland) return
        val packet = event.packet as? ClientboundOpenScreenPacket ?: return
        val title = packet.title.string
        val detected = when {
            title.startsWith("Chronomatron (") -> Mode.CHRONO
            title.startsWith("Ultrasequencer (") -> Mode.ULTRA
            else -> null
        }
        if (mode != detected) {
            reset()
            mode = detected
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        val current = mode ?: return
        if (!enabled) return reset()
        val screen = mc.gui.screen() as? ContainerScreen ?: return reset()
        when (current) {
            Mode.CHRONO -> stepChronomatron(screen.menu)
            Mode.ULTRA -> stepUltrasequencer(screen.menu)
        }
    }

    private fun stepChronomatron(h: ChestMenu) {
        val pivot = slotAt(h, PIVOT_SLOT).item
        if (pivot == Items.GLOWSTONE && lastCapturedSlot >= 0 && !slotAt(h, lastCapturedSlot).hasFoil()) {
            if (sequence.size > chronoCap) {
                mc.connection?.send(ServerboundContainerClosePacket(h.containerId))
            }
            sequenceCaptured = false
            return
        }
        if (pivot != Items.CLOCK) return
        if (!sequenceCaptured) {
            val freshSlot = CHRONO_SLOTS.firstOrNull { slotAt(h, it).hasFoil() } ?: return
            sequence = sequence + freshSlot
            lastCapturedSlot = freshSlot
            sentCount = 0
            sequenceCaptured = true
        }
        sendNextClick(h)
    }

    private fun stepUltrasequencer(h: ChestMenu) {
        val pivot = slotAt(h, PIVOT_SLOT).item
        if (pivot == Items.CLOCK) {
            if (sequenceCaptured) {
                sequenceCaptured = false
                sentCount = 0
            }
            sendNextClick(h)
            return
        }
        if (pivot != Items.GLOWSTONE || sequenceCaptured) return
        if (slotAt(h, ULTRA_FILL_SLOT).isEmpty) return
        sequence = ULTRA_SLOTS.mapNotNull { idx ->
            val stack = slotAt(h, idx)
            if (stack.isEmpty) return@mapNotNull null
            if (!stack.hoverName.string.noControlCodes.matches(NUMERIC_NAME)) return@mapNotNull null
            stack.count - 1 to idx
        }.sortedBy { it.first }.map { it.second }
        if (sequence.size > ultraCap) {
            mc.connection?.send(ServerboundContainerClosePacket(h.containerId))
        }
        sequenceCaptured = true
        sentCount = 0
    }

    private fun sendNextClick(h: ChestMenu) {
        if (sentCount >= sequence.size) return
        if (!packetClock.hasTimePassed(delayMs.toLong())) return
        val slotIdx = sequence[sentCount]
        PacketOrderManager.register(PacketOrderManager.Phase.ITEM_USE) {
            ContainerClicks.input(h.containerId, slotIdx)
        }
        packetClock.update()
        sentCount++
    }

    private fun slotAt(h: ChestMenu, idx: Int): ItemStack {
        return h.slots.getOrNull(idx)?.item ?: ItemStack.EMPTY
    }

    private fun reset() {
        sequence = emptyList()
        sentCount = 0
        sequenceCaptured = false
        lastCapturedSlot = -1
        mode = null
    }
}
