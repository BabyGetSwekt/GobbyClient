package gobby.features.floor7

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.events.ChatReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.features.render.BlockHighlighter
import gobby.utils.ChatUtils.modMessage
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import java.awt.Color

object DebuffAreaRenderer : BlockHighlighter() {

    data class RenderArea(val x1: Int, val y: Int, val z1: Int, val x2: Int, val z2: Int, val color: Color)

    private val renderAreas = listOf(
        RenderArea(20, 5, 54, 28, 64, Color(255, 0, 0, 255)),       // Red
        RenderArea(18, 5, 85, 28, 99, Color(0, 255, 0, 255)),       // Green
        RenderArea(52, 7, 123, 61, 132, Color(128, 0, 128, 255)),   // Purple
        RenderArea(83, 5, 89, 92, 102, Color(0, 0, 255, 255)),      // Blue
        RenderArea(81, 5, 51, 90, 60, Color(255, 165, 0, 255)),     // Orange
    )

    private var active = false

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!GobbyConfig.p5DebuffHelper || !GobbyConfig.renderDebuffArea) return
        if (event.message.startsWith("[BOSS] The Wither King: You") && event.message.contains("again?")) {
            modMessage("P5 start detected, rendering areas")
            active = true
            scanLoadedChunks()
        }
    }

    @SubscribeEvent
    fun onWorldSwap(event: WorldLoadEvent) {
        active = false
    }

    override fun isEnabled(): Boolean = GobbyConfig.p5DebuffHelper && GobbyConfig.renderDebuffArea && active

    override fun getStatePredicate(): (BlockState) -> Boolean = { !it.isAir }

    override fun getColor(pos: BlockPos): Color {
        return renderAreas.firstOrNull { area ->
            pos.y == area.y &&
            pos.x in area.x1..area.x2 &&
            pos.z in area.z1..area.z2
        }?.color ?: Color(255, 255, 255, 255)
    }

    override fun depthTest(): Boolean = true

    override fun isValidPosition(pos: BlockPos): Boolean {
        val world = mc.world ?: return false
        val above = world.getBlockState(pos.up())
        if (!above.isAir && !above.isOf(Blocks.LIGHT_GRAY_CARPET)) return false
        return renderAreas.any { area ->
            pos.y == area.y &&
            pos.x in area.x1..area.x2 &&
            pos.z in area.z1..area.z2
        }
    }
}
