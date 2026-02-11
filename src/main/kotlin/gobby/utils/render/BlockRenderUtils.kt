package gobby.utils.render

import com.mojang.blaze3d.systems.RenderSystem
import gobby.Gobbyclient.Companion.mc
import gobby.events.render.NewRender3DEvent
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.Camera
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.Tessellator
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import org.joml.Matrix3f
import org.joml.Matrix4f
import java.awt.Color

object BlockRenderUtils {

    fun draw3DBox(
        matrixStack: MatrixStack,
        camera: Camera,
        box: Box,
        color: Color,
        filled: Boolean = true
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

        // Draw if filled
        if (filled) {
            val bufferBuilder = vertexConsumerProvider.getBuffer(RenderLayers.ESP_QUADS)

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

            vertexConsumerProvider.draw(RenderLayers.ESP_QUADS)
        }

        // Box outline
        val bufferBuilder = vertexConsumerProvider.getBuffer(RenderLayers.ESP_LINES)

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

        vertexConsumerProvider.draw(RenderLayers.ESP_LINES)
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
}