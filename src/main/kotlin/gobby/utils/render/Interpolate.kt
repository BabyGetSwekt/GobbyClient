package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import gobby.utils.Utils.cameraPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
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

    fun interpolatedEyePos(): Vec3 {
        val player = mc.player ?: return Vec3(0.0, 0.0, 0.0)
        return player.getEyePosition(mc.deltaTracker.getGameTimeDeltaPartialTick(false))
    }

    fun interpolatedEyeVec(): Vec3 {
        val player = mc.player ?: return Vec3(0.0, 0.0, 0.0)
        return player.getEyePosition(mc.deltaTracker.getGameTimeDeltaPartialTick(false))
    }

    fun interpolateEntity(entity: Entity): Vec3 {
        val x = interpolateLastTickPos(entity.x, entity.xOld)
        val y = interpolateLastTickPos(entity.y, entity.yOld)
        val z = interpolateLastTickPos(entity.z, entity.zOld)
        return Vec3(x, y, z)
    }

    fun interpolatedLookVec(distance: Double = 4.0): Vec3 {
        //? if >26.1.2
        val camera = mc.gameRenderer.mainCamera()
        //? if <=26.1.2
        /*val camera = mc.gameRenderer.mainCamera*/

        val yaw = camera.yRot()
        val pitch = camera.xRot()
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        val x = -sin(yawRad) * cos(pitchRad)
        val y = -sin(pitchRad)
        val z = cos(yawRad) * cos(pitchRad)

        val lookVec = Vec3(x, y, z).normalize()
        return interpolatedEyePos().add(lookVec.scale(distance))
    }



    fun interpolateLastTickPos(pos: Double, lastPos: Double): Double {
        return lastPos + (pos - lastPos) * mc.deltaTracker.getGameTimeDeltaPartialTick(false)
    }

    fun interpolatedEyeVec(player: Player): Vec3 {
        return player.getEyePosition(mc.deltaTracker.getGameTimeDeltaPartialTick(false))
    }

    fun interpolateVectors(vec: Vec3): Vec3 {
        val x = vec.x - renderPosX
        val y = vec.y - renderPosY
        val z = vec.z - renderPosZ
        return Vec3(x, y, z)
    }

    /**
     * Gets the interpolated Vec3 position of an entity (i.e. position based on render ticks)
     *
     * @param entity The entity to get the position for
     * @param tickDelta The render time
     * @return The interpolated vector of an entity
     */
    fun getRenderPosition(entity: Entity, tickDelta: Float): Vec3 {
        return Vec3(
            entity.x - Mth.lerp(tickDelta, entity.xOld.toFloat(), entity.x.toFloat()),
            entity.y - Mth.lerp(tickDelta, entity.yOld.toFloat(), entity.y.toFloat()),
            entity.z - Mth.lerp(tickDelta, entity.zOld.toFloat(), entity.z.toFloat())
        )
    }

    fun interpolatePos(pos: BlockPos): AABB {
        return interpolatePos(pos, 1.0f)
    }

    fun interpolatePos(pos: BlockPos, height: Float): AABB {
        return AABB(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + height.toDouble(), pos.z + 1.0
        )
    }

    fun getLerpedBox(e: Entity, partialTicks: Float): AABB {
        if (e.isRemoved()) return e.boundingBox

        val offset = getRenderPosition(e, partialTicks).subtract(e.x, e.y, e.z)
        return e.boundingBox.move(offset)
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
        //? if >26.1.2
        get() = mc.gameRenderer.mainCamera().cameraPos.x
        //? if <=26.1.2
        /*get() = mc.gameRenderer.mainCamera.cameraPos.x*/

    val renderPosY: Double
        //? if >26.1.2
        get() = mc.gameRenderer.mainCamera().cameraPos.y
        //? if <=26.1.2
        /*get() = mc.gameRenderer.mainCamera.cameraPos.y*/

    val renderPosZ: Double
        //? if >26.1.2
        get() = mc.gameRenderer.mainCamera().cameraPos.z
        //? if <=26.1.2
        /*get() = mc.gameRenderer.mainCamera.cameraPos.z*/
}
