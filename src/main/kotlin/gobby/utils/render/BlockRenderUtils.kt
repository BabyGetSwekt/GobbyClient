package gobby.utils.render

import gobby.utils.Utils.cameraPos
import net.minecraft.client.Camera
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
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
        val collector = RenderUtils.frameCollector ?: return
        val adjusted = box.move(camera.cameraPos.scale(-1.0))
        val rgba = colorComponents(color)
        val quadLayer = if (depthTest) ItemBlockRenderTypes.DEPTH_QUADS else ItemBlockRenderTypes.ESP_QUADS
        val lineLayer = if (depthTest) ItemBlockRenderTypes.DEPTH_LINES else ItemBlockRenderTypes.ESP_LINES
        if (filled) submitBoxFaces(matrixStack, collector, quadLayer, adjusted, rgba)
        submitBoxEdges(matrixStack, camera, collector, lineLayer, box, color)
    }

    private fun colorComponents(color: Color): FloatArray =
        floatArrayOf(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)

    private fun submitBoxFaces(
        matrixStack: PoseStack,
        collector: SubmitNodeCollector,
        layer: RenderType,
        box: AABB,
        rgba: FloatArray
    ) {
        collector.submitGeometry(matrixStack, layer) { pose, buffer ->
            addBoxFaces(pose, buffer, box, rgba)
        }
    }

    private fun addBoxFaces(pose: PoseStack.Pose, buffer: VertexConsumer, box: AABB, rgba: FloatArray) {
        val (r, g, b, a) = rgba
        buffer.addVertex(pose, box.minX.toFloat(), box.minY.toFloat(), box.minZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.maxX.toFloat(), box.minY.toFloat(), box.minZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.maxX.toFloat(), box.minY.toFloat(), box.maxZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.minX.toFloat(), box.minY.toFloat(), box.maxZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.minX.toFloat(), box.maxY.toFloat(), box.minZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.minX.toFloat(), box.maxY.toFloat(), box.maxZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.maxX.toFloat(), box.maxY.toFloat(), box.maxZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.maxX.toFloat(), box.maxY.toFloat(), box.minZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.minX.toFloat(), box.minY.toFloat(), box.minZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.minX.toFloat(), box.maxY.toFloat(), box.minZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.maxX.toFloat(), box.maxY.toFloat(), box.minZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.maxX.toFloat(), box.minY.toFloat(), box.minZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.maxX.toFloat(), box.minY.toFloat(), box.minZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.maxX.toFloat(), box.maxY.toFloat(), box.minZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.maxX.toFloat(), box.maxY.toFloat(), box.maxZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.maxX.toFloat(), box.minY.toFloat(), box.maxZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.minX.toFloat(), box.minY.toFloat(), box.maxZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.maxX.toFloat(), box.minY.toFloat(), box.maxZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.maxX.toFloat(), box.maxY.toFloat(), box.maxZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.minX.toFloat(), box.maxY.toFloat(), box.maxZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.minX.toFloat(), box.minY.toFloat(), box.minZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.minX.toFloat(), box.minY.toFloat(), box.maxZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.minX.toFloat(), box.maxY.toFloat(), box.maxZ.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, box.minX.toFloat(), box.maxY.toFloat(), box.minZ.toFloat()).setColor(r, g, b, a)
    }

    private fun submitBoxEdges(
        matrixStack: PoseStack,
        camera: Camera,
        collector: SubmitNodeCollector,
        layer: RenderType,
        box: AABB,
        color: Color
    ) {
        collector.submitGeometry(matrixStack, layer) { pose, buffer ->
            buildLine3D(pose, camera, buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, color)
            buildLine3D(pose, camera, buffer, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, color)
            buildLine3D(pose, camera, buffer, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color)
            buildLine3D(pose, camera, buffer, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, color)
            buildLine3D(pose, camera, buffer, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, color)
            buildLine3D(pose, camera, buffer, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, color)
            buildLine3D(pose, camera, buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, color)
            buildLine3D(pose, camera, buffer, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, color)
            buildLine3D(pose, camera, buffer, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color)
            buildLine3D(pose, camera, buffer, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color)
            buildLine3D(pose, camera, buffer, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color)
            buildLine3D(pose, camera, buffer, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color)
        }
    }

    private val EDGE_CORNERS = listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1)
    private val EDGE_CELLS = listOf(-1 to -1, -1 to 0, 0 to -1, 0 to 0)

    private data class BlockEdge(val axis: Direction.Axis, val corner: BlockPos)

    fun drawConnectedBlocks(
        matrixStack: PoseStack,
        camera: Camera,
        blocks: Collection<BlockPos>,
        color: Color,
        edgeColor: Color = Color(color.red, color.green, color.blue, 255),
        depthTest: Boolean = false
    ) {
        if (blocks.isEmpty()) return
        val collector = RenderUtils.frameCollector ?: return
        val filled = blocks.toHashSet()
        val offset = camera.cameraPos.scale(-1.0)
        val rgba = colorComponents(color)
        val quadLayer = if (depthTest) ItemBlockRenderTypes.DEPTH_QUADS else ItemBlockRenderTypes.ESP_QUADS
        val lineLayer = if (depthTest) ItemBlockRenderTypes.DEPTH_LINES else ItemBlockRenderTypes.ESP_LINES
        collector.submitGeometry(matrixStack, quadLayer) { pose, buffer ->
            exposedFaces(filled).forEach { (pos, dir) -> addFace(pose, buffer, AABB(pos).move(offset), dir, rgba) }
        }
        collector.submitGeometry(matrixStack, lineLayer) { pose, buffer ->
            silhouetteEdges(filled).forEach { edge -> addEdge(pose, camera, buffer, edge, edgeColor) }
        }
    }

    private fun exposedFaces(filled: Set<BlockPos>): List<Pair<BlockPos, Direction>> =
        filled.flatMap { pos -> Direction.entries.filter { pos.relative(it) !in filled }.map { pos to it } }

    private fun silhouetteEdges(filled: Set<BlockPos>): List<BlockEdge> =
        filled.flatMapTo(HashSet(), ::edgesOf).filter { edge ->
            EDGE_CELLS.count { (first, second) -> axisPos(edge.axis, edge.corner, 0, first, second) in filled } % 2 == 1
        }

    private fun edgesOf(pos: BlockPos): List<BlockEdge> =
        Direction.Axis.entries.flatMap { axis ->
            EDGE_CORNERS.map { (first, second) -> BlockEdge(axis, axisPos(axis, pos, 0, first, second)) }
        }

    private fun axisPos(axis: Direction.Axis, base: BlockPos, along: Int, first: Int, second: Int): BlockPos = when (axis) {
        Direction.Axis.X -> BlockPos(base.x + along, base.y + first, base.z + second)
        Direction.Axis.Y -> BlockPos(base.x + first, base.y + along, base.z + second)
        else -> BlockPos(base.x + first, base.y + second, base.z + along)
    }

    private fun addEdge(pose: PoseStack.Pose, camera: Camera, buffer: VertexConsumer, edge: BlockEdge, color: Color) {
        val start = edge.corner
        val end = axisPos(edge.axis, start, 1, 0, 0)
        buildLine3D(
            pose, camera, buffer,
            start.x.toDouble(), start.y.toDouble(), start.z.toDouble(),
            end.x.toDouble(), end.y.toDouble(), end.z.toDouble(),
            color
        )
    }

    private fun addFace(pose: PoseStack.Pose, buffer: VertexConsumer, box: AABB, dir: Direction, rgba: FloatArray) {
        val (r, g, b, a) = rgba
        faceCorners(box, dir).forEach { (x, y, z) -> buffer.addVertex(pose, x, y, z).setColor(r, g, b, a) }
    }

    private fun faceCorners(box: AABB, dir: Direction): List<Triple<Float, Float, Float>> {
        val minX = box.minX.toFloat()
        val minY = box.minY.toFloat()
        val minZ = box.minZ.toFloat()
        val maxX = box.maxX.toFloat()
        val maxY = box.maxY.toFloat()
        val maxZ = box.maxZ.toFloat()
        return when (dir) {
            Direction.DOWN -> listOf(Triple(minX, minY, minZ), Triple(maxX, minY, minZ), Triple(maxX, minY, maxZ), Triple(minX, minY, maxZ))
            Direction.UP -> listOf(Triple(minX, maxY, minZ), Triple(minX, maxY, maxZ), Triple(maxX, maxY, maxZ), Triple(maxX, maxY, minZ))
            Direction.NORTH -> listOf(Triple(minX, minY, minZ), Triple(minX, maxY, minZ), Triple(maxX, maxY, minZ), Triple(maxX, minY, minZ))
            Direction.EAST -> listOf(Triple(maxX, minY, minZ), Triple(maxX, maxY, minZ), Triple(maxX, maxY, maxZ), Triple(maxX, minY, maxZ))
            Direction.SOUTH -> listOf(Triple(minX, minY, maxZ), Triple(maxX, minY, maxZ), Triple(maxX, maxY, maxZ), Triple(minX, maxY, maxZ))
            else -> listOf(Triple(minX, minY, minZ), Triple(minX, minY, maxZ), Triple(minX, maxY, maxZ), Triple(minX, maxY, minZ))
        }
    }

    fun drawNode(
        matrixStack: PoseStack,
        camera: Camera,
        center: Vec3,
        halfSize: Double,
        color: Color,
        filled: Boolean = true,
        depthTest: Boolean = false
    ) {
        val box = AABB(
            center.x - halfSize, center.y - halfSize, center.z - halfSize,
            center.x + halfSize, center.y + halfSize, center.z + halfSize
        )
        draw3DBox(matrixStack, camera, box, color, filled, depthTest)
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
        val collector = RenderUtils.frameCollector ?: return
        val layer = if (depthTest) ItemBlockRenderTypes.DEPTH_LINES else ItemBlockRenderTypes.ESP_LINES
        collector.submitGeometry(matrixStack, layer) { pose, buffer ->
            buildLine3D(pose, camera, buffer, x1, y1, z1, x2, y2, z2, color)
        }
    }

    fun buildLine3D(
        pose: PoseStack.Pose,
        camera: Camera,
        buffer: VertexConsumer,
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        color: Color
    ) {
        val cameraPos = camera.cameraPos
        val dir = Vec3(x2 - x1, y2 - y1, z2 - z1).normalize()
        val r = color.red.toFloat() / 255f
        val g = color.green.toFloat() / 255f
        val b = color.blue.toFloat() / 255f
        val a = color.alpha.toFloat() / 255f
        buffer.addVertex(pose, (x1 - cameraPos.x).toFloat(), (y1 - cameraPos.y).toFloat(), (z1 - cameraPos.z).toFloat())
            .setColor(r, g, b, a).setLineWidth(2f).setNormal(pose, dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())
        buffer.addVertex(pose, (x2 - cameraPos.x).toFloat(), (y2 - cameraPos.y).toFloat(), (z2 - cameraPos.z).toFloat())
            .setColor(r, g, b, a).setLineWidth(2f).setNormal(pose, dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())
    }

    fun drawCylinder(
        matrixStack: PoseStack,
        camera: Camera,
        centerX: Double,
        centerY: Double,
        centerZ: Double,
        widthX: Double,
        widthZ: Double,
        height: Double,
        color: Color,
        filled: Boolean = false,
        segments: Int = 64,
        depthTest: Boolean = false
    ) {
        val collector = RenderUtils.frameCollector ?: return
        val cameraPos = camera.cameraPos
        val rgba = colorComponents(color)
        val radiusX = widthX / 2.0
        val radiusZ = widthZ / 2.0
        val bottom = centerY - cameraPos.y
        val top = centerY + height - cameraPos.y
        val center = Vec3(centerX - cameraPos.x, 0.0, centerZ - cameraPos.z)
        val cosValues = DoubleArray(segments + 1) { index -> cos(2.0 * PI * index / segments) }
        val sinValues = DoubleArray(segments + 1) { index -> sin(2.0 * PI * index / segments) }
        if (filled) submitCylinderSurface(matrixStack, collector, depthTest, segments, center, radiusX, radiusZ, bottom, top, cosValues, sinValues, rgba)
        submitCylinderOutline(matrixStack, collector, depthTest, segments, center, radiusX, radiusZ, bottom, top, cosValues, sinValues, rgba)
    }

    private fun submitCylinderSurface(
        matrixStack: PoseStack,
        collector: SubmitNodeCollector,
        depthTest: Boolean,
        segments: Int,
        center: Vec3,
        radiusX: Double,
        radiusZ: Double,
        bottom: Double,
        top: Double,
        cosValues: DoubleArray,
        sinValues: DoubleArray,
        rgba: FloatArray
    ) {
        val layer = if (depthTest) ItemBlockRenderTypes.DEPTH_QUADS else ItemBlockRenderTypes.ESP_QUADS
        collector.submitGeometry(matrixStack, layer) { pose, buffer ->
            for (index in 0 until segments) {
                val first = cylinderPoint(center, radiusX, radiusZ, cosValues[index], sinValues[index])
                val second = cylinderPoint(center, radiusX, radiusZ, cosValues[index + 1], sinValues[index + 1])
                addCylinderQuad(buffer, pose, first, second, bottom, top, rgba)
            }
        }
    }

    private fun cylinderPoint(center: Vec3, radiusX: Double, radiusZ: Double, cosine: Double, sine: Double): Vec3 =
        Vec3(center.x + cosine * radiusX, 0.0, center.z + sine * radiusZ)

    private fun addCylinderQuad(
        buffer: VertexConsumer,
        pose: PoseStack.Pose,
        first: Vec3,
        second: Vec3,
        bottom: Double,
        top: Double,
        rgba: FloatArray
    ) {
        val (r, g, b, a) = rgba
        buffer.addVertex(pose, first.x.toFloat(), bottom.toFloat(), first.z.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, second.x.toFloat(), bottom.toFloat(), second.z.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, second.x.toFloat(), top.toFloat(), second.z.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(pose, first.x.toFloat(), top.toFloat(), first.z.toFloat()).setColor(r, g, b, a)
    }

    private fun submitCylinderOutline(
        matrixStack: PoseStack,
        collector: SubmitNodeCollector,
        depthTest: Boolean,
        segments: Int,
        center: Vec3,
        radiusX: Double,
        radiusZ: Double,
        bottom: Double,
        top: Double,
        cosValues: DoubleArray,
        sinValues: DoubleArray,
        rgba: FloatArray
    ) {
        val layer = if (depthTest) ItemBlockRenderTypes.DEPTH_LINES else ItemBlockRenderTypes.ESP_LINES
        collector.submitGeometry(matrixStack, layer) { pose, buffer ->
            for (index in 0 until segments) {
                val first = cylinderPoint(center, radiusX, radiusZ, cosValues[index], sinValues[index])
                val second = cylinderPoint(center, radiusX, radiusZ, cosValues[index + 1], sinValues[index + 1])
                buildLineRaw(pose, buffer, first.x, bottom, first.z, second.x, bottom, second.z, rgba[0], rgba[1], rgba[2], rgba[3])
                buildLineRaw(pose, buffer, first.x, top, first.z, second.x, top, second.z, rgba[0], rgba[1], rgba[2], rgba[3])
            }
        }
    }

    fun drawCone(
        matrixStack: PoseStack,
        camera: Camera,
        centerX: Double, centerY: Double, centerZ: Double,
        radius: Double,
        height: Double,
        brimOffset: Double = 0.0,
        yaw: Float = 0f,
        pitch: Float = 0f,
        segments: Int = 48,
        depthTest: Boolean = false,
        colorFor: (segment: Int, apex: Boolean) -> Color
    ) {
        val collector = RenderUtils.frameCollector ?: return
        val cameraPos = camera.cameraPos
        val orientation = Quaternionf()
            .rotateY(Math.toRadians(-yaw.toDouble()).toFloat()).rotateX(Math.toRadians(pitch.toDouble()).toFloat())
        val cosValues = DoubleArray(segments + 1) { i -> cos(2.0 * PI * i / segments) }
        val sinValues = DoubleArray(segments + 1) { i -> sin(2.0 * PI * i / segments) }
        val apexOffset = brimOffset + height
        val quadsLayer = if (depthTest) ItemBlockRenderTypes.DEPTH_QUADS else ItemBlockRenderTypes.ESP_QUADS
        collector.submitGeometry(matrixStack, quadsLayer) { pose, buf ->
            val local = Vector3f()

            fun v(lx: Double, ly: Double, lz: Double, c: Color) {
                local.set(lx.toFloat(), ly.toFloat(), lz.toFloat())
                orientation.transform(local)
                buf.addVertex(pose, (centerX + local.x - cameraPos.x).toFloat(), (centerY + local.y - cameraPos.y).toFloat(), (centerZ + local.z - cameraPos.z).toFloat()).setColor(c.red / 255f, c.green / 255f, c.blue / 255f, c.alpha / 255f)
            }
            for (i in 0 until segments) {
                v(cosValues[i] * radius, brimOffset, sinValues[i] * radius, colorFor(i, false))
                v(cosValues[i + 1] * radius, brimOffset, sinValues[i + 1] * radius, colorFor(i + 1, false))
                v(0.0, apexOffset, 0.0, colorFor(i, true))
                v(0.0, apexOffset, 0.0, colorFor(i, true))
            }
        }
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
        buffer.addVertex(entry, x1.toFloat(), y1.toFloat(), z1.toFloat())
            .setColor(r, g, b, a).setLineWidth(2f).setNormal(entry, nx, ny, nz)
        buffer.addVertex(entry, x2.toFloat(), y2.toFloat(), z2.toFloat())
            .setColor(r, g, b, a).setLineWidth(2f).setNormal(entry, nx, ny, nz)
    }
}
