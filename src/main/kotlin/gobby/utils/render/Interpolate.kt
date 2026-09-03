package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * Contents of this file are based on Aoba-Client and the work of coltonk9043 under GNU General Public License v3.0.
 * All the credits go to him.
 * @author coltonk9043 (https://github.com/coltonk9043)
 * License: https://github.com/coltonk9043/Aoba-Client/blob/master/LICENSE
 * Original source: https://github.com/coltonk9043/Aoba-Client/blob/53607ef4318a9e5a246fb2a347ec25ec184b15a8/src/main/java/net/aoba/utils/Interpolation.java
 */

object Interpolate {

    fun partialTick(): Float = mc.deltaTracker.getGameTimeDeltaPartialTick(false)

    fun interpolatedEyePos(): Vec3 = mc.player?.getEyePosition(partialTick()) ?: Vec3.ZERO

    fun interpolatedLookVec(distance: Double = 4.0): Vec3 {
        val camera = mc.gameRenderer.mainCamera()
        val yaw = Math.toRadians(camera.yRot().toDouble())
        val pitch = Math.toRadians(camera.xRot().toDouble())
        val look = Vec3(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch)).normalize()
        return interpolatedEyePos().add(look.scale(distance))
    }

    fun getRenderPosition(entity: Entity, tickDelta: Float = partialTick()): Vec3 = Vec3(
        Mth.lerp(tickDelta.toDouble(), entity.xOld, entity.x),
        Mth.lerp(tickDelta.toDouble(), entity.yOld, entity.y),
        Mth.lerp(tickDelta.toDouble(), entity.zOld, entity.z)
    )

    fun interpolateEntity(entity: Entity): Vec3 = getRenderPosition(entity)

    fun getLerpedBox(entity: Entity, tickDelta: Float = partialTick()): AABB {
        if (entity.isRemoved) return entity.boundingBox
        return entity.boundingBox.move(getRenderPosition(entity, tickDelta).subtract(entity.x, entity.y, entity.z))
    }

    fun interpolateColorC(color1: Color, color2: Color, amount: Float): Color {
        val factor = amount.coerceIn(0f, 1f)
        return Color(
            interpolateInt(color1.red, color2.red, factor),
            interpolateInt(color1.green, color2.green, factor),
            interpolateInt(color1.blue, color2.blue, factor),
            interpolateInt(color1.alpha, color2.alpha, factor)
        )
    }

    fun interpolateInt(oldValue: Int, newValue: Int, factor: Float): Int =
        interpolate(oldValue.toDouble(), newValue.toDouble(), factor.toDouble()).toInt()

    fun interpolate(oldValue: Double, newValue: Double, factor: Double): Double =
        oldValue + (newValue - oldValue) * factor
}
