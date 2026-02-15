package gobby.features.floor7.devices

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.events.RightClickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.DungeonUtils.getPhase
import gobby.utils.timer.Clock
import net.minecraft.entity.decoration.ItemFrameEntity
import net.minecraft.util.hit.EntityHitResult
import kotlin.math.floor

object AlignHelper {

    private val clickClocks = mutableMapOf<Int, Clock>()

    @SubscribeEvent
    fun onRightClick(event: RightClickEvent) {
        if (!inDungeons || !inBoss || dungeonFloor != 7 || getPhase() != 3) return
        if (!GobbyConfig.alignHelper || mc.player == null || mc.world == null) return

        val frame = getTargetedFrame() ?: return
        val index = getFrameIndex(frame) ?: return
        val frameData = AutoAlign.currentFrames?.get(index) ?: return
        val solution = AutoAlign.currentSolution ?: return

        if (!AutoAlign.remainingClicks.containsKey(index)) {
            if (GobbyConfig.alignSneakOverride && mc.player!!.isSneaking) return
            event.cancel()
            return
        }

        registerClick(index, frameData, solution[index] ?: return)
    }

    private fun getTargetedFrame(): ItemFrameEntity? {
        val hitResult = mc.crosshairTarget as? EntityHitResult ?: return null
        return hitResult.entity as? ItemFrameEntity
    }

    private fun getFrameIndex(frame: ItemFrameEntity): Int? {
        val corner = AutoAlign.deviceCornerPos
        val x = floor(frame.x).toInt()
        if (x != corner.x) return null

        val dy = floor(frame.y).toInt() - corner.y
        val dz = floor(frame.z).toInt() - corner.z
        val index = dy + dz * 5

        return if (index in 0..24) index else null
    }

    private fun registerClick(index: Int, frameData: AutoAlign.FrameData, target: Int) {
        val clock = clickClocks.getOrPut(index) { Clock() }
        clock.update()
        AutoAlign.recentClicks[index] = clock.lastTime

        frameData.rotation = (frameData.rotation + 1) % 8

        val remaining = (target - frameData.rotation + 8) % 8
        if (remaining == 0) {
            AutoAlign.remainingClicks.remove(index)
        } else {
            AutoAlign.remainingClicks[index] = remaining
        }
    }
}
