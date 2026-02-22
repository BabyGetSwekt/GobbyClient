package gobby.features.floor7

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.features.Triggerbot
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.Utils.getBlockAtPos
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos

object LeverTriggerbot : Triggerbot() {

    enum class LeverType { DEVICE, P3 }

    private val leverPositions = mapOf(
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
        inDungeons && dungeonFloor == 7 && inBoss && mc.currentScreen != null &&
            (GobbyConfig.lightsDeviceTriggerbot || GobbyConfig.p3LeverTriggerbot)

    override fun isValidBlock(pos: BlockPos): Boolean {
        val world = mc.world ?: return false
        if (world.getBlockAtPos(pos) != Blocks.LEVER) return false
        val type = leverPositions[pos] ?: return false
        return when (type) {
            LeverType.DEVICE -> GobbyConfig.lightsDeviceTriggerbot
            LeverType.P3 -> GobbyConfig.p3LeverTriggerbot
        }
    }
}
