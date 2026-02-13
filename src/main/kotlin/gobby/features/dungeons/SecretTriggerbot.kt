package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.PlayerUtils
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.timer.Clock
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos

object SecretTriggerbot {

    private val clock = Clock()
    private var clickDelay = 0L
    private var targetPos: BlockPos? = null
    private val clickedBlocks = mutableMapOf<BlockPos, Long>()

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (mc.player == null || mc.world == null || !inDungeons || inBoss) return
        if (!GobbyConfig.secretTriggerbot) return

        val now = System.currentTimeMillis()
        clickedBlocks.entries.removeIf { now - it.value > 5000 }

        val hitResult = mc.crosshairTarget
        if (hitResult !is BlockHitResult || hitResult.type != HitResult.Type.BLOCK) {
            targetPos = null
            return
        }

        val pos = hitResult.blockPos
        if (pos in clickedBlocks) return

        if (!DungeonUtils.isSecret(pos)) {
            targetPos = null
            return
        }

        if (targetPos != pos) {
            targetPos = pos
            clickDelay = (50L..100L).random()
            clock.update()
            return
        }

        if (!clock.hasTimePassed(clickDelay)) return

        PlayerUtils.rightClick()
        clickedBlocks[BlockPos(pos)] = now
        targetPos = null
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        clickedBlocks.clear()
        targetPos = null
    }
}
