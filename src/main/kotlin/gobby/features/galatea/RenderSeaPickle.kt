package gobby.features.galatea

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.render.WorldUtils
import gobby.utils.timer.Clock
import net.minecraft.block.SeaPickleBlock
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

object RenderSeaPickle {

    private val matchingPickles = mutableListOf<BlockPos>()
    private val timer = Clock(2000)

    @SubscribeEvent
    fun onRender(event: NewRender3DEvent) {
        val player = mc.player ?: return
        val world = mc.world ?: return
        if (!GobbyConfig.seaLumiesESP) return

        if (timer.hasTimePassed(setTime = true)) {
            matchingPickles.clear()

            val chunkDistance = mc.options.viewDistance.value.coerceAtMost(8)
            val radius = chunkDistance * 16

            BlockPos.iterate(
                player.blockPos.add(-radius, -radius, -radius),
                player.blockPos.add(radius, radius, radius)
            ).forEach { pos ->
                val state = world.getBlockState(pos)
                if (state.block is SeaPickleBlock) {
                    val pickleCount = state.get(Properties.PICKLES)
                    if (pickleCount >= GobbyConfig.seaLumiesAmount) {
                        matchingPickles.add(pos)
                    }
                }
            }
        }
        for (pos in matchingPickles) {
            val box = Box(pos)
            draw3DBox(event.matrixStack, event.camera, box, GobbyConfig.seaLumiesColor)
        }
    }
}