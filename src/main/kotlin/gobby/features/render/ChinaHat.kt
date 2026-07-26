package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render3DEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.SelectorSetting
import gobby.utils.isNpc
import gobby.utils.render.BlockRenderUtils
import gobby.utils.render.Interpolate
import gobby.utils.timer.Clock
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import java.awt.Color
import kotlin.math.PI
import kotlin.math.cos

object ChinaHat : Module("China Hat", "Wear a straw china hat above your head", Category.RENDER) {

    private val style by SelectorSetting("Style", 0, listOf("Normal", "Rainbow"), desc = "Hat appearance")
    private val others by BooleanSetting("Other Players", false, desc = "Also render on other players")

    private const val STYLE_RAINBOW = 1
    private const val BASE_RADIUS = 0.7
    private const val BASE_HEIGHT = 0.28
    private const val HEAD_RAISE = 0.03
    private const val NECK_FRACTION = 0.83
    private const val SEGMENTS = 64
    private const val RAINBOW_PERIOD = 4000L
    private const val STRANDS = 16
    private const val STRAND_DEPTH = 0.13
    private const val HAT_ALPHA = 255

    private val STRAW_TOP = Color(236, 214, 165)
    private val STRAW_BRIM = Color(163, 126, 80)

    private val rainbowClock = Clock()

    @SubscribeEvent
    fun onRender3D(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity || !enabled) return
        val world = mc.level ?: return
        val pose = event.context.poseStack()
        val camera = mc.gameRenderer.mainCamera()
        if (others) world.players().filter { !it.isNpc() }.forEach { renderHat(it, pose, camera) }
        else mc.player?.let { renderHat(it, pose, camera) }
    }

    private fun renderHat(player: Player, pose: PoseStack, camera: Camera) {
        if (player === mc.player && mc.options.cameraType.isFirstPerson) return
        val partial = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val scale = player.scale.toDouble()
        val pos = Interpolate.interpolateEntity(player)
        val neckY = pos.y + player.bbHeight * NECK_FRACTION
        val brimOffset = player.bbHeight * (1.0 - NECK_FRACTION) + HEAD_RAISE * scale
        BlockRenderUtils.drawCone(
            pose, camera,
            pos.x, neckY, pos.z,
            BASE_RADIUS * scale, BASE_HEIGHT * scale,
            brimOffset, Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot), player.getViewXRot(partial),
            SEGMENTS, depthTest = true
        ) { segment, apex -> hatColor(segment, apex) }
    }

    private fun hatColor(segment: Int, apex: Boolean): Color {
        if (style == STYLE_RAINBOW) {
            val phase = (rainbowClock.getTime() % RAINBOW_PERIOD).toFloat() / RAINBOW_PERIOD
            val hue = (segment.toFloat() / SEGMENTS + phase) % 1f
            val c = Color.getHSBColor(hue, 1f, 1f)
            return Color(c.red, c.green, c.blue, HAT_ALPHA)
        }
        val strand = 1.0 - STRAND_DEPTH * (0.5 + 0.5 * cos(segment.toDouble() / SEGMENTS * 2 * PI * STRANDS))
        return (if (apex) STRAW_TOP else STRAW_BRIM).shade(strand)
    }

    private fun Color.shade(factor: Double): Color = Color(
        (red * factor).toInt().coerceIn(0, 255),
        (green * factor).toInt().coerceIn(0, 255),
        (blue * factor).toInt().coerceIn(0, 255),
        alpha
    )
}
