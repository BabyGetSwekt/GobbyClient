package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.PlayerUtils
import gobby.utils.Utils.equalsOneOf
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.skyblock.dungeon.ScanUtils.currentRoom
import gobby.utils.timer.Clock
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos

object SecretTriggerbot {

    private val clock = Clock()
    private const val clickDelay = 100L
    private val clickedBlocks = mutableMapOf<BlockPos, Long>()



    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (mc.player == null || mc.world == null || !inDungeons || inBoss) return
        if (!GobbyConfig.secretTriggerbot || currentRoom?.data?.name.equalsOneOf("Water Board", "Three Weirdos")) return
        if (!clock.hasTimePassed(clickDelay)) return

        val now = System.currentTimeMillis()
        clickedBlocks.entries.removeIf { now - it.value > 5000 }

        val hitResult = mc.crosshairTarget
        if (hitResult !is BlockHitResult || hitResult.type != HitResult.Type.BLOCK) return

        val pos = hitResult.blockPos
        if (pos in clickedBlocks) return
        if (!DungeonUtils.isSecret(pos)) return

        PlayerUtils.rightClick()
        clock.update()
        clickedBlocks[pos] = now
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        clickedBlocks.clear()
    }
}
