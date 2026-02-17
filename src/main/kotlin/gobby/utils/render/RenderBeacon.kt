package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.utils.render.BlockRenderUtils.buildLine3D
import gobby.utils.timer.Clock
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.Camera
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RotationAxis
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object RenderBeacon {

    private data class BeaconData(
        val pos: BlockPos,
        val color: Color,
        val label: String?,
        val setTime: Long = System.currentTimeMillis()
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

    @SubscribeEvent
    fun onRender(event: NewRender3DEvent) {
        if (beacons.isEmpty()) return

        if (cleanupClock.hasTimePassed(30_000, setTime = true)) {
            val now = System.currentTimeMillis()
            beacons.removeIf { beacon -> now - beacon.setTime > 30_000 }
        }

        for (beacon in beacons) {
            renderBeaconBeam(event.matrixStack, event.camera, beacon)
            if (beacon.label != null) {
                renderBeaconText(event.matrixStack, event.camera, beacon)
            }
        }
    }

    private fun renderBeaconBeam(matrixStack: MatrixStack, camera: Camera, beacon: BeaconData) {
        val vertexConsumerProvider = mc.bufferBuilders.entityVertexConsumers
        val layer = RenderLayers.ESP_QUADS
        val buffer = vertexConsumerProvider.getBuffer(layer)

        val entry = matrixStack.peek()
        val matrix4f = entry.positionMatrix
        val cameraPos = camera.pos


        // Color values
        val r = beacon.color.red.toFloat() / 255f
        val g = beacon.color.green.toFloat() / 255f
        val b = beacon.color.blue.toFloat() / 255f
        val a = beacon.color.alpha.toFloat() / 255f

        val beaconX = beacon.pos.x + 0.5 - cameraPos.x
        val beaconY = beacon.pos.y + 1.0 - cameraPos.y
        val beaconZ = beacon.pos.z + 0.5 - cameraPos.z

        for (i in 0 until SEGMENTS) {
            val angle1 = (i * 2.0 * Math.PI / SEGMENTS).toFloat()
            val angle2 = ((i + 1) * 2.0 * Math.PI / SEGMENTS).toFloat()

            val x1 = beaconX + cos(angle1) * BEAM_RADIUS
            val z1 = beaconZ + sin(angle1) * BEAM_RADIUS
            val x2 = beaconX + cos(angle2) * BEAM_RADIUS
            val z2 = beaconZ + sin(angle2) * BEAM_RADIUS

            // Bottom quad (beacon level)
            buffer.vertex(matrix4f, x1.toFloat(), beaconY.toFloat(), z1.toFloat()).color(r, g, b, a)
            buffer.vertex(matrix4f, x2.toFloat(), beaconY.toFloat(), z2.toFloat()).color(r, g, b, a)
            buffer.vertex(matrix4f, x2.toFloat(), (beaconY + BEAM_HEIGHT).toFloat(), z2.toFloat()).color(r, g, b, a * 0.3f)
            buffer.vertex(matrix4f, x1.toFloat(), (beaconY + BEAM_HEIGHT).toFloat(), z1.toFloat()).color(r, g, b, a * 0.3f)
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
            buffer.vertex(matrix4f, x1.toFloat(), beaconY.toFloat(), z1.toFloat()).color(r, g, b, a * 0.8f)
            buffer.vertex(matrix4f, x2.toFloat(), beaconY.toFloat(), z2.toFloat()).color(r, g, b, a * 0.8f)
            buffer.vertex(matrix4f, x2.toFloat(), (beaconY + BEAM_HEIGHT).toFloat(), z2.toFloat()).color(r, g, b, a * 0.1f)
            buffer.vertex(matrix4f, x1.toFloat(), (beaconY + BEAM_HEIGHT).toFloat(), z1.toFloat()).color(r, g, b, a * 0.1f)
        }

        vertexConsumerProvider.draw(layer)

        val lineLayer = RenderLayers.ESP_LINES
        val lineBuffer = vertexConsumerProvider.getBuffer(lineLayer)

        val baseSize = 0.6f
        val baseY = beacon.pos.y + 1.0

        buildLine3D(matrixStack, camera, lineBuffer,
            beacon.pos.x + 0.5 - baseSize/2, baseY, beacon.pos.z + 0.5 - baseSize/2,
            beacon.pos.x + 0.5 + baseSize/2, baseY, beacon.pos.z + 0.5 - baseSize/2, beacon.color)
        buildLine3D(matrixStack, camera, lineBuffer,
            beacon.pos.x + 0.5 + baseSize/2, baseY, beacon.pos.z + 0.5 - baseSize/2,
            beacon.pos.x + 0.5 + baseSize/2, baseY, beacon.pos.z + 0.5 + baseSize/2, beacon.color)
        buildLine3D(matrixStack, camera, lineBuffer,
            beacon.pos.x + 0.5 + baseSize/2, baseY, beacon.pos.z + 0.5 + baseSize/2,
            beacon.pos.x + 0.5 - baseSize/2, baseY, beacon.pos.z + 0.5 + baseSize/2, beacon.color)
        buildLine3D(matrixStack, camera, lineBuffer,
            beacon.pos.x + 0.5 - baseSize/2, baseY, beacon.pos.z + 0.5 + baseSize/2,
            beacon.pos.x + 0.5 - baseSize/2, baseY, beacon.pos.z + 0.5 - baseSize/2, beacon.color)

        vertexConsumerProvider.draw(lineLayer)
    }

    private fun calculateDistance(cameraPos: Vec3d, beaconPos: BlockPos): Double {
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

    private fun renderBeaconText(matrixStack: MatrixStack, camera: Camera, beacon: BeaconData) {
        val textRenderer = mc.textRenderer
        val cameraPos = camera.pos

        val distance = calculateDistance(cameraPos, beacon.pos)
        val scale = calculateTextScale(distance)

        matrixStack.push()

        val textX = beacon.pos.x + 0.5 - cameraPos.x
        val textY = beacon.pos.y + 2.5 - cameraPos.y
        val textZ = beacon.pos.z + 0.5 - cameraPos.z

        matrixStack.translate(textX, textY, textZ)

        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.yaw))
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.pitch))

        matrixStack.scale(-scale, -scale, scale)
        val textWidth = textRenderer.getWidth(beacon.label)

        val immediate = mc.bufferBuilders.entityVertexConsumers
        textRenderer.draw(
            beacon.label,
            -textWidth / 2f,
            0f,
            0xFFFFFF,
            false,
            matrixStack.peek().positionMatrix,
            immediate,
            TextRenderer.TextLayerType.SEE_THROUGH,
            0x40000000,
            15728880
        )

        immediate.drawCurrentLayer()
        matrixStack.pop()
    }
}