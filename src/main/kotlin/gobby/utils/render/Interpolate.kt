package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Contents of this file are based on Aoba-Client and the work of coltonk9043 under GNU General Public License v3.0.
 * All the credits go to him.
 * @author coltonk9043 (https://github.com/coltonk9043)
 * License: https://github.com/coltonk9043/Aoba-Client/blob/master/LICENSE
 * Original source: https://github.com/coltonk9043/Aoba-Client/blob/53607ef4318a9e5a246fb2a347ec25ec184b15a8/src/main/java/net/aoba/utils/Interpolation.java
 */
object Interpolate {

    fun interpolatedEyePos(): Vec3d {
        val player = mc.player ?: return Vec3d(0.0, 0.0, 0.0)
        return player.getCameraPosVec(mc.renderTickCounter.getTickProgress(false))
    }

    fun interpolatedEyeVec(): Vec3d {
        val player = mc.player ?: return Vec3d(0.0, 0.0, 0.0)
        return player.getClientCameraPosVec(mc.renderTickCounter.getTickProgress(false))
    }

    fun interpolateEntity(entity: Entity): Vec3d {
        val x = interpolateLastTickPos(entity.x, entity.lastX)
        val y = interpolateLastTickPos(entity.y, entity.lastY)
        val z = interpolateLastTickPos(entity.z, entity.lastZ)
        return Vec3d(x, y, z)
    }

    fun interpolatedLookVec(distance: Double = 4.0): Vec3d {
        val camera = mc.gameRenderer.camera ?: return Vec3d.ZERO

        val yawRad = Math.toRadians(camera.yaw.toDouble())
        val pitchRad = Math.toRadians(camera.pitch.toDouble())

        val x = -sin(yawRad) * cos(pitchRad)
        val y = -sin(pitchRad)
        val z = cos(yawRad) * cos(pitchRad)

        val lookVec = Vec3d(x, y, z).normalize()
        return interpolatedEyePos().add(lookVec.multiply(distance))
    }



    fun interpolateLastTickPos(pos: Double, lastPos: Double): Double {
        return lastPos + (pos - lastPos) * mc.renderTickCounter.getTickProgress(false)
    }

    fun interpolatedEyeVec(player: PlayerEntity): Vec3d {
        return player.getClientCameraPosVec(mc.renderTickCounter.getTickProgress(false))
    }

    fun interpolateVectors(vec: Vec3d): Vec3d {
        val x = vec.x - renderPosX
        val y = vec.y - renderPosY
        val z = vec.z - renderPosZ
        return Vec3d(x, y, z)
    }

    /**
     * Gets the interpolated Vec3d position of an entity (i.e. position based on render ticks)
     *
     * @param entity The entity to get the position for
     * @param tickDelta The render time
     * @return The interpolated vector of an entity
     */
    fun getRenderPosition(entity: Entity, tickDelta: Float): Vec3d {
        return Vec3d(
            entity.x - MathHelper.lerp(tickDelta, entity.lastRenderX.toFloat(), entity.x.toFloat()),
            entity.y - MathHelper.lerp(tickDelta, entity.lastRenderY.toFloat(), entity.y.toFloat()),
            entity.z - MathHelper.lerp(tickDelta, entity.lastRenderZ.toFloat(), entity.z.toFloat())
        )
    }

    fun interpolatePos(pos: BlockPos): Box {
        return interpolatePos(pos, 1.0f)
    }

    fun interpolatePos(pos: BlockPos, height: Float): Box {
        return Box(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + height.toDouble(), pos.z + 1.0
        )
    }

    fun getLerpedBox(e: Entity, partialTicks: Float): Box {
        if (e.isRemoved) return e.boundingBox

        val offset = getRenderPosition(e, partialTicks).subtract(e.x, e.y, e.z)
        return e.boundingBox.offset(offset)
    }

    fun interpolateColorC(color1: Color, color2: Color, amount: Float): Color {
        val clampedAmount = amount.coerceIn(0.0f, 1.0f)
        return Color(
            interpolateInt(color1.red, color2.red, clampedAmount),
            interpolateInt(color1.green, color2.green, clampedAmount),
            interpolateInt(color1.blue, color2.blue, clampedAmount),
            interpolateInt(color1.alpha, color2.alpha, clampedAmount)
        )
    }

    fun interpolateInt(oldValue: Int, newValue: Int, interpolationValue: Float): Int {
        return interpolate(oldValue.toDouble(), newValue.toDouble(), interpolationValue.toDouble()).toInt()
    }

    fun interpolateFloat(prev: Float, value: Float, factor: Float): Float {
        return prev + ((value - prev) * factor)
    }

    fun interpolate(oldValue: Double, newValue: Double, interpolationValue: Double): Double {
        return oldValue + (newValue - oldValue) * interpolationValue
    }

    val renderPosX: Double
        get() = mc.gameRenderer.camera.pos.x

    val renderPosY: Double
        get() = mc.gameRenderer.camera.pos.y

    val renderPosZ: Double
        get() = mc.gameRenderer.camera.pos.z
}
