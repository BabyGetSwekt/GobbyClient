package gobby.features.floor7

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.features.Triggerbot
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.DungeonUtils.isDead
import gobby.utils.Utils.getBlockAtPos
import gobby.utils.managers.AuraManager
import gobby.utils.timer.Clock
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

object P3Levers : Triggerbot(
    "Levers", "Auto right-clicks Floor 7 levers",
    Category.FLOOR7
) {

    private val triggerbot by BooleanSetting("Triggerbot", false, desc = "Right-clicks levers you're looking at")
    private val aura by BooleanSetting("Aura", false, desc = "Automatically flicks levers in range without looking")
    val lightsDevice by BooleanSetting("Lights Device", false, desc = "Aura's or uses triggerbot on s2 device (depending on your settings)")
    val p3Levers by BooleanSetting("P3 Levers", false, desc = "Aura's or uses triggerbot on p3 levers (depending on your settings)")


    private val auraClock = Clock()
    private val auraCooldowns = mutableMapOf<BlockPos, Clock>()

    private const val AURA_RANGE_SQ = 25.0
    private const val AURA_DELAY = 100L
    private const val AURA_COOLDOWN = 5000L

    enum class LeverType { DEVICE, P3 }

    val leverPositions = mapOf(
        BlockPos(62, 133, 142) to LeverType.DEVICE,
        BlockPos(62, 136, 142) to LeverType.DEVICE,
        BlockPos(60, 135, 142) to LeverType.DEVICE,
        BlockPos(60, 134, 142) to LeverType.DEVICE,
        BlockPos(58, 136, 142) to LeverType.DEVICE,
        BlockPos(58, 133, 142) to LeverType.DEVICE,

        BlockPos(106, 124, 113) to LeverType.P3,
        BlockPos(94, 124, 113) to LeverType.P3,
        BlockPos(23, 132, 138) to LeverType.P3,
        BlockPos(27, 124, 127) to LeverType.P3,
        BlockPos(2, 122, 55) to LeverType.P3,
        BlockPos(14, 122, 55) to LeverType.P3,
        BlockPos(84, 121, 34) to LeverType.P3,
        BlockPos(86, 128, 46) to LeverType.P3,
    )

    override fun getClickDelay(): Long = 50L

    override fun shouldActivate(): Boolean =
        enabled && inDungeons && dungeonFloor == 7 && inBoss && !isDead && mc.currentScreen == null &&
            (lightsDevice || p3Levers)

    override fun isValidBlock(pos: BlockPos): Boolean {
        val world = mc.world ?: return false
        if (world.getBlockAtPos(pos) != Blocks.LEVER) return false
        val type = leverPositions[pos] ?: return false
        return when (type) {
            LeverType.DEVICE -> lightsDevice
            LeverType.P3 -> p3Levers
        }
    }

    @SubscribeEvent
    override fun onTick(event: ClientTickEvent.Pre) {
        if (triggerbot) super.onTick(event)
        if (!aura) return
        if (!shouldActivate()) return
        if (!auraClock.hasTimePassed(AURA_DELAY)) return

        if (auraCooldowns.isNotEmpty()) auraCooldowns.entries.removeIf { it.value.hasTimePassed(AURA_COOLDOWN) }

        val player = mc.player ?: return
        val world = mc.world ?: return
        val eyePos = player.eyePos

        for ((pos, type) in leverPositions) {
            val typeEnabled = when (type) {
                LeverType.DEVICE -> lightsDevice
                LeverType.P3 -> p3Levers
            }
            if (!typeEnabled) continue
            if (pos in auraCooldowns) continue

            val center = Vec3d.ofCenter(pos)
            if (eyePos.squaredDistanceTo(center) > AURA_RANGE_SQ) continue
            if (world.getBlockAtPos(pos) != Blocks.LEVER) continue

            AuraManager.auraBlock(pos)
            auraClock.update()
            auraCooldowns[pos] = Clock()
            return
        }
    }
}
