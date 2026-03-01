package gobby.features

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.PlayerUtils
import gobby.utils.timer.Clock
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos

abstract class Triggerbot(
    name: String,
    description: String = "",
    category: Category,
    defaultEnabled: Boolean = false
) : Module(name, description, category, toggled = true, defaultEnabled = defaultEnabled) {

    protected val clock = Clock()
    protected val clickedBlocks = mutableMapOf<BlockPos, Long>()

    @SubscribeEvent
    open fun onTick(event: ClientTickEvent.Pre) {
        if (mc.player == null || mc.world == null) return
        if (!shouldActivate()) return
        if (!clock.hasTimePassed(getClickDelay())) return

        val now = System.currentTimeMillis()
        clickedBlocks.entries.removeIf { now - it.value > getBlockCooldown() }

        val pos = getTargetPos() ?: return
        if (pos in clickedBlocks) return
        if (!isValidBlock(pos)) return

        performAction()
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
    open fun getBlockCooldown(): Long = 5000L

    protected open fun getTargetPos(): BlockPos? {
        val hitResult = mc.crosshairTarget
        if (hitResult !is BlockHitResult || hitResult.type != HitResult.Type.BLOCK) return null
        return hitResult.blockPos
    }

    protected open fun performAction() {
        PlayerUtils.rightClick()
    }
}
