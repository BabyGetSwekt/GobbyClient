package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import net.minecraft.client.render.Camera
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
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
        matrixStack: MatrixStack,
        camera: Camera,
        box: Box,
        color: Color,
        filled: Boolean = true,
        depthTest: Boolean = false
    ) {
        if (matrixStack == null) return
        val newBox = box.offset(camera.pos.multiply(-1.0))

        val entry = matrixStack.peek()
        val matrix4f: Matrix4f = entry.positionMatrix

        val r = color.red.toFloat() / 255f
        val g = color.green.toFloat() / 255f
        val b = color.blue.toFloat() / 255f
        val a = color.alpha.toFloat() / 255f

        val vertexConsumerProvider = mc.bufferBuilders.entityVertexConsumers
        val quadsLayer = if (depthTest) RenderLayers.DEPTH_QUADS else RenderLayers.ESP_QUADS
        val linesLayer = if (depthTest) RenderLayers.DEPTH_LINES else RenderLayers.ESP_LINES

        // Draw if filled
        if (filled) {
            val bufferBuilder = vertexConsumerProvider.getBuffer(quadsLayer)

            bufferBuilder.vertex(matrix4f, newBox.minX.toFloat(), newBox.minY.toFloat(), newBox.minZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.maxX.toFloat(), newBox.minY.toFloat(), newBox.minZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.maxX.toFloat(), newBox.minY.toFloat(), newBox.maxZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.minX.toFloat(), newBox.minY.toFloat(), newBox.maxZ.toFloat()).color(r, g, b, a)

            bufferBuilder.vertex(matrix4f, newBox.minX.toFloat(), newBox.maxY.toFloat(), newBox.minZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.minX.toFloat(), newBox.maxY.toFloat(), newBox.maxZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.maxX.toFloat(), newBox.maxY.toFloat(), newBox.maxZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.maxX.toFloat(), newBox.maxY.toFloat(), newBox.minZ.toFloat()).color(r, g, b, a)

            bufferBuilder.vertex(matrix4f, newBox.minX.toFloat(), newBox.minY.toFloat(), newBox.minZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.minX.toFloat(), newBox.maxY.toFloat(), newBox.minZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.maxX.toFloat(), newBox.maxY.toFloat(), newBox.minZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.maxX.toFloat(), newBox.minY.toFloat(), newBox.minZ.toFloat()).color(r, g, b, a)

            bufferBuilder.vertex(matrix4f, newBox.maxX.toFloat(), newBox.minY.toFloat(), newBox.minZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.maxX.toFloat(), newBox.maxY.toFloat(), newBox.minZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.maxX.toFloat(), newBox.maxY.toFloat(), newBox.maxZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.maxX.toFloat(), newBox.minY.toFloat(), newBox.maxZ.toFloat()).color(r, g, b, a)

            bufferBuilder.vertex(matrix4f, newBox.minX.toFloat(), newBox.minY.toFloat(), newBox.maxZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.maxX.toFloat(), newBox.minY.toFloat(), newBox.maxZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.maxX.toFloat(), newBox.maxY.toFloat(), newBox.maxZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.minX.toFloat(), newBox.maxY.toFloat(), newBox.maxZ.toFloat()).color(r, g, b, a)

            bufferBuilder.vertex(matrix4f, newBox.minX.toFloat(), newBox.minY.toFloat(), newBox.minZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.minX.toFloat(), newBox.minY.toFloat(), newBox.maxZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.minX.toFloat(), newBox.maxY.toFloat(), newBox.maxZ.toFloat()).color(r, g, b, a)
            bufferBuilder.vertex(matrix4f, newBox.minX.toFloat(), newBox.maxY.toFloat(), newBox.minZ.toFloat()).color(r, g, b, a)

            vertexConsumerProvider.draw(quadsLayer)
        }

        // Box outline
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

        vertexConsumerProvider.draw(linesLayer)
    }

    fun drawLine3D(
        matrixStack: MatrixStack,
        camera: Camera,
        pos1Last: Vec3d, pos1Current: Vec3d,
        pos2Last: Vec3d, pos2Current: Vec3d,
        tickDelta: Float,
        color: Color
    ) {
        val interpPos1 = pos1Last.lerp(pos1Current, tickDelta.toDouble())
        val interpPos2 = pos2Last.lerp(pos2Current, tickDelta.toDouble())

        drawLine3D(matrixStack, camera, interpPos1, interpPos2, color)
    }

    fun drawLine3D(
        matrixStack: MatrixStack,
        camera: Camera,
        pos1: Vec3d, pos2: Vec3d,
        color: Color
    ) {
        drawLine3D(matrixStack, camera, pos1.x, pos1.y, pos1.z, pos2.x, pos2.y, pos2.z, color)
    }

    fun drawLine3D(
        matrixStack: MatrixStack,
        camera: Camera,
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        color: Color
    ) {
        val vertexConsumers = mc.bufferBuilders.entityVertexConsumers
        val layer = RenderLayers.ESP_LINES
        val buffer = vertexConsumers.getBuffer(layer)
        buildLine3D(matrixStack, camera, buffer, x1, y1, z1, x2, y2, z2, color)
        vertexConsumers.draw(layer)
    }

    fun buildLine3D(
        matrixStack: MatrixStack,
        camera: Camera,
        buffer: VertexConsumer,
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        color: Color
    ) {
        val entry = matrixStack.peek()
        val matrix4f = entry.positionMatrix
        val cameraPos = camera.pos

        val dir = Vec3d(x2 - x1, y2 - y1, z2 - z1).normalize()

        val r = color.red.toFloat() / 255f
        val g = color.green.toFloat() / 255f
        val b = color.blue.toFloat() / 255f
        val a = color.alpha.toFloat() / 255f

        buffer.vertex(matrix4f, (x1 - cameraPos.x).toFloat(), (y1 - cameraPos.y).toFloat(), (z1 - cameraPos.z).toFloat())
            .color(r, g, b, a).normal(entry, dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())

        buffer.vertex(matrix4f, (x2 - cameraPos.x).toFloat(), (y2 - cameraPos.y).toFloat(), (z2 - cameraPos.z).toFloat())
            .color(r, g, b, a).normal(entry, dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())
    }

    fun drawCylinder(
        matrixStack: MatrixStack,
        camera: Camera,
        centerX: Double, centerY: Double, centerZ: Double,
        widthX: Double, widthZ: Double,
        height: Double,
        color: Color,
        filled: Boolean = false,
        segments: Int = 64,
        depthTest: Boolean = false
    ) {
        val cameraPos = camera.pos
        val entry = matrixStack.peek()
        val matrix4f = entry.positionMatrix
        val vertexConsumerProvider = mc.bufferBuilders.entityVertexConsumers

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
            val quadsLayer = if (depthTest) RenderLayers.DEPTH_QUADS else RenderLayers.ESP_QUADS
            val buf = vertexConsumerProvider.getBuffer(quadsLayer)

            for (i in 0 until segments) {
                val x1 = (cx + cosValues[i] * radiusX).toFloat()
                val z1 = (cz + sinValues[i] * radiusZ).toFloat()
                val x2 = (cx + cosValues[i + 1] * radiusX).toFloat()
                val z2 = (cz + sinValues[i + 1] * radiusZ).toFloat()

                buf.vertex(matrix4f, x1, yBottom, z1).color(r, g, b, a)
                buf.vertex(matrix4f, x2, yBottom, z2).color(r, g, b, a)
                buf.vertex(matrix4f, x2, yTop, z2).color(r, g, b, a)
                buf.vertex(matrix4f, x1, yTop, z1).color(r, g, b, a)
            }

            vertexConsumerProvider.draw(quadsLayer)
        }

        val linesLayer = if (depthTest) RenderLayers.DEPTH_LINES else RenderLayers.ESP_LINES
        val lineBuf = vertexConsumerProvider.getBuffer(linesLayer)

        for (i in 0 until segments) {
            val wx1 = cx + cosValues[i] * radiusX
            val wz1 = cz + sinValues[i] * radiusZ
            val wx2 = cx + cosValues[i + 1] * radiusX
            val wz2 = cz + sinValues[i + 1] * radiusZ

            buildLineRaw(entry, lineBuf, wx1, yBottom.toDouble(), wz1, wx2, yBottom.toDouble(), wz2, r, g, b, a)
            buildLineRaw(entry, lineBuf, wx1, yTop.toDouble(), wz1, wx2, yTop.toDouble(), wz2, r, g, b, a)
        }

        vertexConsumerProvider.draw(linesLayer)
    }

    fun drawRing(
        matrixStack: MatrixStack,
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
        entry: MatrixStack.Entry,
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

        buffer.vertex(entry.positionMatrix, x1.toFloat(), y1.toFloat(), z1.toFloat())
            .color(r, g, b, a).normal(entry, nx, ny, nz)
        buffer.vertex(entry.positionMatrix, x2.toFloat(), y2.toFloat(), z2.toFloat())
            .color(r, g, b, a).normal(entry, nx, ny, nz)
    }
}