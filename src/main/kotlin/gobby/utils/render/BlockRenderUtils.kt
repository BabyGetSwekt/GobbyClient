package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import gobby.utils.Utils.cameraPos
import net.minecraft.client.Camera
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import java.awt.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Contents of this file are based on Aoba-Client and the work of coltonk9043 under GNU General Public License v3.0.
 * All the credits go to him.
 * @author coltonk9043 (https://github.com/coltonk9043)
 * License: https://github.com/coltonk9043/Aoba-Client/blob/master/LICENSE
 * Original source: https://github.com/coltonk9043/Aoba-Client/blob/53607ef4318a9e5a246fb2a347ec25ec184b15a8/src/main/java/net/aoba/utils/render/Render3D.java
 */
object BlockRenderUtils {

    fun draw3DBox(
        matrixStack: PoseStack,
        camera: Camera,
        box: AABB,
        color: Color,
        filled: Boolean = true,
        depthTest: Boolean = false
    ) {
        if (matrixStack == null) return
        val newBox = box.move(camera.cameraPos.scale(-1.0))

        val entry = matrixStack.last()
        val matrix4f: Matrix4f = entry.pose()

        val r = color.red.toFloat() / 255f
        val g = color.green.toFloat() / 255f
        val b = color.blue.toFloat() / 255f
        val a = color.alpha.toFloat() / 255f

        val vertexConsumerProvider = mc.renderBuffers().bufferSource()
        val quadsLayer = if (depthTest) ItemBlockRenderTypes.DEPTH_QUADS else ItemBlockRenderTypes.ESP_QUADS
        val linesLayer = if (depthTest) ItemBlockRenderTypes.DEPTH_LINES else ItemBlockRenderTypes.ESP_LINES

        // Draw if filled
        if (filled) {
            val bufferBuilder = vertexConsumerProvider.getBuffer(quadsLayer)

            bufferBuilder.addVertex(matrix4f, newBox.minX.toFloat(), newBox.minY.toFloat(), newBox.minZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.maxX.toFloat(), newBox.minY.toFloat(), newBox.minZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.maxX.toFloat(), newBox.minY.toFloat(), newBox.maxZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.minX.toFloat(), newBox.minY.toFloat(), newBox.maxZ.toFloat()).setColor(r, g, b, a)

            bufferBuilder.addVertex(matrix4f, newBox.minX.toFloat(), newBox.maxY.toFloat(), newBox.minZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.minX.toFloat(), newBox.maxY.toFloat(), newBox.maxZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.maxX.toFloat(), newBox.maxY.toFloat(), newBox.maxZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.maxX.toFloat(), newBox.maxY.toFloat(), newBox.minZ.toFloat()).setColor(r, g, b, a)

            bufferBuilder.addVertex(matrix4f, newBox.minX.toFloat(), newBox.minY.toFloat(), newBox.minZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.minX.toFloat(), newBox.maxY.toFloat(), newBox.minZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.maxX.toFloat(), newBox.maxY.toFloat(), newBox.minZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.maxX.toFloat(), newBox.minY.toFloat(), newBox.minZ.toFloat()).setColor(r, g, b, a)

            bufferBuilder.addVertex(matrix4f, newBox.maxX.toFloat(), newBox.minY.toFloat(), newBox.minZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.maxX.toFloat(), newBox.maxY.toFloat(), newBox.minZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.maxX.toFloat(), newBox.maxY.toFloat(), newBox.maxZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.maxX.toFloat(), newBox.minY.toFloat(), newBox.maxZ.toFloat()).setColor(r, g, b, a)

            bufferBuilder.addVertex(matrix4f, newBox.minX.toFloat(), newBox.minY.toFloat(), newBox.maxZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.maxX.toFloat(), newBox.minY.toFloat(), newBox.maxZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.maxX.toFloat(), newBox.maxY.toFloat(), newBox.maxZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.minX.toFloat(), newBox.maxY.toFloat(), newBox.maxZ.toFloat()).setColor(r, g, b, a)

            bufferBuilder.addVertex(matrix4f, newBox.minX.toFloat(), newBox.minY.toFloat(), newBox.minZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.minX.toFloat(), newBox.minY.toFloat(), newBox.maxZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.minX.toFloat(), newBox.maxY.toFloat(), newBox.maxZ.toFloat()).setColor(r, g, b, a)
            bufferBuilder.addVertex(matrix4f, newBox.minX.toFloat(), newBox.maxY.toFloat(), newBox.minZ.toFloat()).setColor(r, g, b, a)

            vertexConsumerProvider.endBatch(quadsLayer)
        }

        // AABB outline
        val bufferBuilder = vertexConsumerProvider.getBuffer(linesLayer)

        buildLine3D(matrixStack, camera, bufferBuilder, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, color)
        buildLine3D(matrixStack, camera, bufferBuilder, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, color)
        buildLine3D(matrixStack, camera, bufferBuilder, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color)
        buildLine3D(matrixStack, camera, bufferBuilder, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, color)
        buildLine3D(matrixStack, camera, bufferBuilder, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, color)
        buildLine3D(matrixStack, camera, bufferBuilder, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, color)
        buildLine3D(matrixStack, camera, bufferBuilder, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, color)
        buildLine3D(matrixStack, camera, bufferBuilder, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, color)
        buildLine3D(matrixStack, camera, bufferBuilder, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color)
        buildLine3D(matrixStack, camera, bufferBuilder, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color)
        buildLine3D(matrixStack, camera, bufferBuilder, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color)
        buildLine3D(matrixStack, camera, bufferBuilder, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color)

        vertexConsumerProvider.endBatch(linesLayer)
    }

    fun drawLine3D(
        matrixStack: PoseStack,
        camera: Camera,
        pos1Last: Vec3, pos1Current: Vec3,
        pos2Last: Vec3, pos2Current: Vec3,
        tickDelta: Float,
        color: Color
    ) {
        val interpPos1 = pos1Last.lerp(pos1Current, tickDelta.toDouble())
        val interpPos2 = pos2Last.lerp(pos2Current, tickDelta.toDouble())

        drawLine3D(matrixStack, camera, interpPos1, interpPos2, color)
    }

    fun drawLine3D(
        matrixStack: PoseStack,
        camera: Camera,
        pos1: Vec3, pos2: Vec3,
        color: Color,
        depthTest: Boolean = false
    ) {
        drawLine3D(matrixStack, camera, pos1.x, pos1.y, pos1.z, pos2.x, pos2.y, pos2.z, color, depthTest)
    }

    fun drawLine3D(
        matrixStack: PoseStack,
        camera: Camera,
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        color: Color,
        depthTest: Boolean = false
    ) {
        val vertexConsumers = mc.renderBuffers().bufferSource()
        val layer = if (depthTest) ItemBlockRenderTypes.DEPTH_LINES else ItemBlockRenderTypes.ESP_LINES
        val buffer = vertexConsumers.getBuffer(layer)
        buildLine3D(matrixStack, camera, buffer, x1, y1, z1, x2, y2, z2, color)
        vertexConsumers.endBatch(layer)
    }

    fun buildLine3D(
        matrixStack: PoseStack,
        camera: Camera,
        buffer: VertexConsumer,
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        color: Color
    ) {
        val entry = matrixStack.last()
        val matrix4f = entry.pose()
        val cameraPos = camera.cameraPos

        val dir = Vec3(x2 - x1, y2 - y1, z2 - z1).normalize()

        val r = color.red.toFloat() / 255f
        val g = color.green.toFloat() / 255f
        val b = color.blue.toFloat() / 255f
        val a = color.alpha.toFloat() / 255f

        //? if <=1.21.10 {
        buffer.addVertex(matrix4f, (x1 - cameraPos.x).toFloat(), (y1 - cameraPos.y).toFloat(), (z1 - cameraPos.z).toFloat())
            .setColor(r, g, b, a).setNormal(entry, dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())

        buffer.addVertex(matrix4f, (x2 - cameraPos.x).toFloat(), (y2 - cameraPos.y).toFloat(), (z2 - cameraPos.z).toFloat())
            .setColor(r, g, b, a).setNormal(entry, dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())
        //?}
        //? if >=1.21.11 {
        /*buffer.addVertex(matrix4f, (x1 - cameraPos.x).toFloat(), (y1 - cameraPos.y).toFloat(), (z1 - cameraPos.z).toFloat())
            .setColor(r, g, b, a).setLineWidth(2f).setNormal(entry, dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())

        buffer.addVertex(matrix4f, (x2 - cameraPos.x).toFloat(), (y2 - cameraPos.y).toFloat(), (z2 - cameraPos.z).toFloat())
            .setColor(r, g, b, a).setLineWidth(2f).setNormal(entry, dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())*/
        //?}
    }

    fun drawCylinder(
        matrixStack: PoseStack,
        camera: Camera,
        centerX: Double, centerY: Double, centerZ: Double,
        widthX: Double, widthZ: Double,
        height: Double,
        color: Color,
        filled: Boolean = false,
        segments: Int = 64,
        depthTest: Boolean = false
    ) {
        val cameraPos = camera.cameraPos
        val entry = matrixStack.last()
        val matrix4f = entry.pose()
        val vertexConsumerProvider = mc.renderBuffers().bufferSource()

        val r = color.red.toFloat() / 255f
        val g = color.green.toFloat() / 255f
        val b = color.blue.toFloat() / 255f
        val a = color.alpha.toFloat() / 255f

        val radiusX = widthX / 2.0
        val radiusZ = widthZ / 2.0
        val yBottom = (centerY - cameraPos.y).toFloat()
        val yTop = (centerY + height - cameraPos.y).toFloat()
        val cx = centerX - cameraPos.x
        val cz = centerZ - cameraPos.z

        val cosValues = DoubleArray(segments + 1) { i -> cos(2.0 * PI * i / segments) }
        val sinValues = DoubleArray(segments + 1) { i -> sin(2.0 * PI * i / segments) }

        if (filled) {
            val quadsLayer = if (depthTest) ItemBlockRenderTypes.DEPTH_QUADS else ItemBlockRenderTypes.ESP_QUADS
            val buf = vertexConsumerProvider.getBuffer(quadsLayer)

            for (i in 0 until segments) {
                val x1 = (cx + cosValues[i] * radiusX).toFloat()
                val z1 = (cz + sinValues[i] * radiusZ).toFloat()
                val x2 = (cx + cosValues[i + 1] * radiusX).toFloat()
                val z2 = (cz + sinValues[i + 1] * radiusZ).toFloat()

                buf.addVertex(matrix4f, x1, yBottom, z1).setColor(r, g, b, a)
                buf.addVertex(matrix4f, x2, yBottom, z2).setColor(r, g, b, a)
                buf.addVertex(matrix4f, x2, yTop, z2).setColor(r, g, b, a)
                buf.addVertex(matrix4f, x1, yTop, z1).setColor(r, g, b, a)
            }

            vertexConsumerProvider.endBatch(quadsLayer)
        }

        val linesLayer = if (depthTest) ItemBlockRenderTypes.DEPTH_LINES else ItemBlockRenderTypes.ESP_LINES
        val lineBuf = vertexConsumerProvider.getBuffer(linesLayer)

        for (i in 0 until segments) {
            val wx1 = cx + cosValues[i] * radiusX
            val wz1 = cz + sinValues[i] * radiusZ
            val wx2 = cx + cosValues[i + 1] * radiusX
            val wz2 = cz + sinValues[i + 1] * radiusZ

            buildLineRaw(entry, lineBuf, wx1, yBottom.toDouble(), wz1, wx2, yBottom.toDouble(), wz2, r, g, b, a)
            buildLineRaw(entry, lineBuf, wx1, yTop.toDouble(), wz1, wx2, yTop.toDouble(), wz2, r, g, b, a)
        }

        vertexConsumerProvider.endBatch(linesLayer)
    }

    fun drawRing(
        matrixStack: PoseStack,
        camera: Camera,
        centerX: Double, centerY: Double, centerZ: Double,
        widthX: Double, widthZ: Double,
        height: Double,
        color: Color,
        segments: Int = 64,
        depthTest: Boolean = false
    ) {
        drawCylinder(matrixStack, camera, centerX, centerY, centerZ, widthX, widthZ, height, color, filled = false, segments = segments, depthTest = depthTest)
    }

    private fun buildLineRaw(
        entry: PoseStack.Pose,
        buffer: VertexConsumer,
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val dx = (x2 - x1).toFloat()
        val dy = (y2 - y1).toFloat()
        val dz = (z2 - z1).toFloat()
        val len = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        val nx = if (len > 0) dx / len else 0f
        val ny = if (len > 0) dy / len else 1f
        val nz = if (len > 0) dz / len else 0f

        //? if <=1.21.10 {
        buffer.addVertex(entry.pose(), x1.toFloat(), y1.toFloat(), z1.toFloat())
            .setColor(r, g, b, a).setNormal(entry, nx, ny, nz)
        buffer.addVertex(entry.pose(), x2.toFloat(), y2.toFloat(), z2.toFloat())
            .setColor(r, g, b, a).setNormal(entry, nx, ny, nz)
        //?}
        //? if >=1.21.11 {
        /*buffer.addVertex(entry.pose(), x1.toFloat(), y1.toFloat(), z1.toFloat())
            .setColor(r, g, b, a).setLineWidth(2f).setNormal(entry, nx, ny, nz)
        buffer.addVertex(entry.pose(), x2.toFloat(), y2.toFloat(), z2.toFloat())
            .setColor(r, g, b, a).setLineWidth(2f).setNormal(entry, nx, ny, nz)*/
        //?}
    }
}


