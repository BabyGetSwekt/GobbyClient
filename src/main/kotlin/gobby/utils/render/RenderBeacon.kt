package gobby.utils.render

import gobby.utils.Utils.cameraPos

import gobby.Gobbyclient.Companion.mc
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render3DEvent
import gobby.events.render.camera
import gobby.events.render.matrixStack
import gobby.utils.render.BlockRenderUtils.buildLine3D
import gobby.utils.timer.Clock
import net.minecraft.client.gui.Font
import net.minecraft.client.Camera
import net.minecraft.client.renderer.SubmitNodeCollector
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
    fun onRender(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity) return
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
        val r = beacon.color.red.toFloat() / 255f
        val g = beacon.color.green.toFloat() / 255f
        val b = beacon.color.blue.toFloat() / 255f
        val a = beacon.color.alpha.toFloat() / 255f
        val x = beacon.pos.x + 0.5 - cameraPos.x
        val y = beacon.pos.y + 1.0 - cameraPos.y
        val z = beacon.pos.z + 0.5 - cameraPos.z
        drawBeamLayer(matrixStack, collector, x, y, z, BEAM_RADIUS, r, g, b, a, a * 0.3f)
        drawBeamLayer(matrixStack, collector, x, y, z, BEAM_RADIUS * 0.6f, r, g, b, a * 0.8f, a * 0.1f)
        renderBeamOutline(matrixStack, camera, collector, beacon)
    }

    private fun drawBeamLayer(
        matrixStack: PoseStack,
        collector: SubmitNodeCollector,
        x: Double,
        y: Double,
        z: Double,
        radius: Float,
        red: Float,
        green: Float,
        blue: Float,
        bottomAlpha: Float,
        topAlpha: Float
    ) {
        collector.submitGeometry(matrixStack, ItemBlockRenderTypes.ESP_QUADS) { pose, buffer ->
            for (index in 0 until SEGMENTS) {
                val first = index * 2.0 * Math.PI / SEGMENTS
                val second = (index + 1) * 2.0 * Math.PI / SEGMENTS
                addBeamQuad(buffer, pose, x, y, z, radius, first, second, red, green, blue, bottomAlpha, topAlpha)
            }
        }
    }

    private fun addBeamQuad(
        buffer: com.mojang.blaze3d.vertex.VertexConsumer,
        pose: PoseStack.Pose,
        x: Double,
        y: Double,
        z: Double,
        radius: Float,
        first: Double,
        second: Double,
        red: Float,
        green: Float,
        blue: Float,
        bottomAlpha: Float,
        topAlpha: Float
    ) {
        val x1 = x + cos(first) * radius
        val z1 = z + sin(first) * radius
        val x2 = x + cos(second) * radius
        val z2 = z + sin(second) * radius
        buffer.addVertex(pose, x1.toFloat(), y.toFloat(), z1.toFloat()).setColor(red, green, blue, bottomAlpha)
        buffer.addVertex(pose, x2.toFloat(), y.toFloat(), z2.toFloat()).setColor(red, green, blue, bottomAlpha)
        buffer.addVertex(pose, x2.toFloat(), (y + BEAM_HEIGHT).toFloat(), z2.toFloat()).setColor(red, green, blue, topAlpha)
        buffer.addVertex(pose, x1.toFloat(), (y + BEAM_HEIGHT).toFloat(), z1.toFloat()).setColor(red, green, blue, topAlpha)
    }

    private fun renderBeamOutline(
        matrixStack: PoseStack,
        camera: Camera,
        collector: SubmitNodeCollector,
        beacon: BeaconData
    ) {
        collector.submitGeometry(matrixStack, ItemBlockRenderTypes.ESP_LINES) { pose, buffer ->
            val base = beacon.pos.y + 1.0
            val half = 0.3
            val x = beacon.pos.x + 0.5
            val z = beacon.pos.z + 0.5
            buildLine3D(pose, camera, buffer, x - half, base, z - half, x + half, base, z - half, beacon.color)
            buildLine3D(pose, camera, buffer, x + half, base, z - half, x + half, base, z + half, beacon.color)
            buildLine3D(pose, camera, buffer, x + half, base, z + half, x - half, base, z + half, beacon.color)
            buildLine3D(pose, camera, buffer, x - half, base, z + half, x - half, base, z - half, beacon.color)
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

