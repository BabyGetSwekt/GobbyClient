package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.render.Render3DEvent
import gobby.events.render.camera
import gobby.events.render.matrixStack
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.render.BlockRenderUtils.drawLine3D
import gobby.utils.render.RenderUtils.drawStringInWorld
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.min

private val SPAWN_COLOR = Color(255, 85, 85)
private val MERGED_COLOR = Color(0, 170, 170)
private val POSITION_COLOR = Color(85, 255, 85)

internal object BloodcampRender {

    fun drawSpawn(event: Render3DEvent, stand: ArmorStand, render: BloodcampHelper.RenderData, partialTick: Float, ping: Float) {
        if (!stand.isAlive) return
        val remaining = BloodcampHelper.remainingFor(stand) ?: return
        val endPoint = BloodcampHelper.interpolate(render.endVector, render.lastEndVector, min(BloodcampHelper.tickTime - render.endUpdated, 100L) / 100f)
        val pingPoint = Vec3(stand.x + render.speed.x * ping, stand.y + render.speed.y * ping, stand.z + render.speed.z * ping)

        val previousEnd = render.lastEndPoint
        val previousPing = render.lastPingPoint
        render.lastEndPoint = endPoint
        render.lastPingPoint = pingPoint

        val endBox = BloodcampHelper.boxAt(BloodcampHelper.interpolate(endPoint, previousEnd, partialTick))
        if (ping < remaining) {
            val pingBox = BloodcampHelper.boxAt(BloodcampHelper.interpolate(pingPoint, previousPing, partialTick))
            draw3DBox(event.matrixStack, event.camera, pingBox, POSITION_COLOR, filled = false, depthTest = true)
            draw3DBox(event.matrixStack, event.camera, endBox, SPAWN_COLOR, filled = false, depthTest = true)
        } else draw3DBox(event.matrixStack, event.camera, endBox, MERGED_COLOR, filled = false, depthTest = true)

        drawLine3D(event.matrixStack, event.camera, render.currVector.add(0.0, 2.0, 0.0), endPoint.add(0.0, 2.0, 0.0), SPAWN_COLOR)

        val seconds = (remaining - OFFSET_MS) / 1000f
        drawStringInWorld("${timeColor(seconds)}${"%.2f".format(seconds)}s", endPoint.add(0.0, 2.0, 0.0), event.matrixStack, event.camera, Color.WHITE, 0.025f)
    }

    fun averagePing(): Float {
        val log = mc.debugOverlay.pingLogger
        val samples = min(log.size(), 20)
        if (samples == 0) return 0f
        return (0 until samples).sumOf { log.get(it) }.toFloat() / samples
    }

    private fun timeColor(seconds: Float): String = when {
        seconds > 1.5f -> "§a"
        seconds > 0.5f -> "§6"
        seconds > 0f -> "§c"
        else -> "§b"
    }
}
