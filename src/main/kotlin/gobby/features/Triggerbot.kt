package gobby.features

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.PlayerUtils
import gobby.utils.timer.Clock
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos

abstract class Triggerbot {

    private val clock = Clock()
    private val clickedBlocks = mutableMapOf<BlockPos, Long>()

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (mc.player == null || mc.world == null) return
        if (!shouldActivate()) return
        if (!clock.hasTimePassed(getClickDelay())) return

        val now = System.currentTimeMillis()
        clickedBlocks.entries.removeIf { now - it.value > 5000 }

        val hitResult = mc.crosshairTarget
        if (hitResult !is BlockHitResult || hitResult.type != HitResult.Type.BLOCK) return

        val pos = hitResult.blockPos
        if (pos in clickedBlocks) return
        if (!isValidBlock(pos)) return

        PlayerUtils.rightClick()
        clock.update()
        clickedBlocks[pos] = now
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        clickedBlocks.clear()
    }

    abstract fun shouldActivate(): Boolean
    abstract fun isValidBlock(pos: BlockPos): Boolean
    open fun getClickDelay(): Long = 100L
}
