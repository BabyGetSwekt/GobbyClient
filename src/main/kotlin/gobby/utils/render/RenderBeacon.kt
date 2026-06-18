package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import gobby.utils.Utils.cameraPos
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.utils.render.BlockRenderUtils.buildLine3D
import gobby.utils.timer.Clock
import net.minecraft.client.gui.Font
import net.minecraft.client.Camera
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.BlockPos
import com.mojang.math.Axis
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object RenderBeacon {

    private data class BeaconData(
        val pos: BlockPos,
        val color: Color,
        val label: String?,
        val setTime: Long = System.currentTimeMillis(),
        val persistent: Boolean = false
    )

    private val beacons = mutableListOf<BeaconData>()
    private val cleanupClock = Clock()
    private const val BEAM_RADIUS = 0.2f
    private const val BEAM_HEIGHT = 256f
    private const val SEGMENTS = 16

    private const val MIN_SCALE = 0.045f
    private const val MAX_SCALE = 0.6f
    private const val MIN_DISTANCE = 5.0
    private const val MAX_DISTANCE = 100.0

    fun addBeacon(pos: BlockPos, color: Color, displayLabel: String?) {
        beacons.add(BeaconData(pos, color, displayLabel))
    }

    fun addPersistentBeacon(pos: BlockPos, color: Color, displayLabel: String?) {
        if (beacons.any { it.persistent && it.pos == pos }) return
        beacons.add(BeaconData(pos, color, displayLabel, persistent = true))
    }

    fun removeBeaconAt(pos: BlockPos) {
        beacons.removeIf { it.pos == pos }
    }

    fun clearPersistent() {
        beacons.removeIf { it.persistent }
    }

    @SubscribeEvent
    fun onRender(event: NewRender3DEvent) {
        if (beacons.isEmpty()) return

        if (cleanupClock.hasTimePassed(30_000, setTime = true)) {
            val now = System.currentTimeMillis()
            beacons.removeIf { beacon -> !beacon.persistent && now - beacon.setTime > 30_000 }
        }

        for (beacon in beacons) {
            renderBeaconBeam(event.matrixStack, event.camera, beacon)
            if (beacon.label != null) {
                renderBeaconText(event.matrixStack, event.camera, beacon)
            }
        }
    }

    private fun renderBeaconBeam(matrixStack: PoseStack, camera: Camera, beacon: BeaconData) {
        val collector = RenderUtils.frameCollector ?: return
        val cameraPos = camera.cameraPos

        // Color values
        val r = beacon.color.red.toFloat() / 255f
        val g = beacon.color.green.toFloat() / 255f
        val b = beacon.color.blue.toFloat() / 255f
        val a = beacon.color.alpha.toFloat() / 255f

        val beaconX = beacon.pos.x + 0.5 - cameraPos.x
        val beaconY = beacon.pos.y + 1.0 - cameraPos.y
        val beaconZ = beacon.pos.z + 0.5 - cameraPos.z

        collector.submitCustomGeometry(matrixStack, ItemBlockRenderTypes.ESP_QUADS) { pose, buffer ->
            for (i in 0 until SEGMENTS) {
                val angle1 = (i * 2.0 * Math.PI / SEGMENTS).toFloat()
                val angle2 = ((i + 1) * 2.0 * Math.PI / SEGMENTS).toFloat()

                val x1 = beaconX + cos(angle1) * BEAM_RADIUS
                val z1 = beaconZ + sin(angle1) * BEAM_RADIUS
                val x2 = beaconX + cos(angle2) * BEAM_RADIUS
                val z2 = beaconZ + sin(angle2) * BEAM_RADIUS

                // Bottom quad (beacon level)
                buffer.addVertex(pose, x1.toFloat(), beaconY.toFloat(), z1.toFloat()).setColor(r, g, b, a)
                buffer.addVertex(pose, x2.toFloat(), beaconY.toFloat(), z2.toFloat()).setColor(r, g, b, a)
                buffer.addVertex(pose, x2.toFloat(), (beaconY + BEAM_HEIGHT).toFloat(), z2.toFloat()).setColor(r, g, b, a * 0.3f)
                buffer.addVertex(pose, x1.toFloat(), (beaconY + BEAM_HEIGHT).toFloat(), z1.toFloat()).setColor(r, g, b, a * 0.3f)
            }

            val innerRadius = BEAM_RADIUS * 0.6f
            for (i in 0 until SEGMENTS) {
                val angle1 = (i * 2.0 * Math.PI / SEGMENTS).toFloat()
                val angle2 = ((i + 1) * 2.0 * Math.PI / SEGMENTS).toFloat()

                val x1 = beaconX + cos(angle1) * innerRadius
                val z1 = beaconZ + sin(angle1) * innerRadius
                val x2 = beaconX + cos(angle2) * innerRadius
                val z2 = beaconZ + sin(angle2) * innerRadius

                // Inner beam quad
                buffer.addVertex(pose, x1.toFloat(), beaconY.toFloat(), z1.toFloat()).setColor(r, g, b, a * 0.8f)
                buffer.addVertex(pose, x2.toFloat(), beaconY.toFloat(), z2.toFloat()).setColor(r, g, b, a * 0.8f)
                buffer.addVertex(pose, x2.toFloat(), (beaconY + BEAM_HEIGHT).toFloat(), z2.toFloat()).setColor(r, g, b, a * 0.1f)
                buffer.addVertex(pose, x1.toFloat(), (beaconY + BEAM_HEIGHT).toFloat(), z1.toFloat()).setColor(r, g, b, a * 0.1f)
            }
        }

        collector.submitCustomGeometry(matrixStack, ItemBlockRenderTypes.ESP_LINES) { pose, lineBuffer ->
            val baseSize = 0.6f
            val baseY = beacon.pos.y + 1.0

            buildLine3D(pose, camera, lineBuffer,
                beacon.pos.x + 0.5 - baseSize/2, baseY, beacon.pos.z + 0.5 - baseSize/2,
                beacon.pos.x + 0.5 + baseSize/2, baseY, beacon.pos.z + 0.5 - baseSize/2, beacon.color)
            buildLine3D(pose, camera, lineBuffer,
                beacon.pos.x + 0.5 + baseSize/2, baseY, beacon.pos.z + 0.5 - baseSize/2,
                beacon.pos.x + 0.5 + baseSize/2, baseY, beacon.pos.z + 0.5 + baseSize/2, beacon.color)
            buildLine3D(pose, camera, lineBuffer,
                beacon.pos.x + 0.5 + baseSize/2, baseY, beacon.pos.z + 0.5 + baseSize/2,
                beacon.pos.x + 0.5 - baseSize/2, baseY, beacon.pos.z + 0.5 + baseSize/2, beacon.color)
            buildLine3D(pose, camera, lineBuffer,
                beacon.pos.x + 0.5 - baseSize/2, baseY, beacon.pos.z + 0.5 + baseSize/2,
                beacon.pos.x + 0.5 - baseSize/2, baseY, beacon.pos.z + 0.5 - baseSize/2, beacon.color)
        }
    }

    private fun calculateDistance(cameraPos: Vec3, beaconPos: BlockPos): Double {
        val dx = beaconPos.x + 0.5 - cameraPos.x
        val dy = beaconPos.y + 1.0 - cameraPos.y
        val dz = beaconPos.z + 0.5 - cameraPos.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }


    private fun calculateTextScale(distance: Double): Float {
        return when {
            distance <= MIN_DISTANCE -> MIN_SCALE
            distance >= MAX_DISTANCE -> MAX_SCALE
            else -> {
                val factor = (distance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE)
                (MIN_SCALE + factor * (MAX_SCALE - MIN_SCALE)).toFloat()
            }
        }
    }

    private fun renderBeaconText(matrixStack: PoseStack, camera: Camera, beacon: BeaconData) {
        val collector = RenderUtils.frameCollector ?: return
        val label = beacon.label ?: return
        val textRenderer = mc.font
        val cameraPos = camera.cameraPos

        val distance = calculateDistance(cameraPos, beacon.pos)
        val scale = calculateTextScale(distance)

        matrixStack.pushPose()

        val textX = beacon.pos.x + 0.5 - cameraPos.x
        val textY = beacon.pos.y + 2.5 - cameraPos.y
        val textZ = beacon.pos.z + 0.5 - cameraPos.z

        matrixStack.translate(textX, textY, textZ)

        val yaw = camera.yRot()
        val pitch = camera.xRot()
        matrixStack.mulPose(Axis.YP.rotationDegrees(-yaw))
        matrixStack.mulPose(Axis.XP.rotationDegrees(pitch))

        matrixStack.scale(-scale, -scale, scale)
        val textWidth = textRenderer.width(label)

        collector.submitText(
            matrixStack,
            -textWidth / 2f,
            0f,
            Component.literal(label).visualOrderText,
            false,
            Font.DisplayMode.SEE_THROUGH,
            15728880,
            0xFFFFFFFF.toInt(),
            0x40000000,
            0
        )

        matrixStack.popPose()
    }
}

