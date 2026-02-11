package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.render.Render3DEvent
import gobby.events.render.NewRender3DEvent
import gobby.utils.render.BlockRenderUtils.drawLine3D
import net.minecraft.client.render.Camera
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.VertexConsumerProvider.Immediate
import net.minecraft.client.render.WorldRenderer
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.LivingEntityRenderer
import net.minecraft.client.render.entity.model.EntityModel
import net.minecraft.client.render.entity.state.LivingEntityRenderState
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityPose
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.RotationAxis
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.cos


object Render3D {

    fun NewRender3DEvent.drawEntityModel(
        matrixStack: MatrixStack, camera: Camera, partialTicks: Float, entity: Entity?,
        color: Color
    ) {
        val renderer: EntityRenderer<*, *>? = mc.entityRenderDispatcher.getRenderer(entity)

        if (entity is LivingEntity) {
            matrixStack.push()

            val leRenderer =
                renderer as LivingEntityRenderer<LivingEntity?, LivingEntityRenderState, EntityModel<LivingEntityRenderState?>>
            val model = leRenderer.getModel()
            val renderState = leRenderer.getAndUpdateRenderState(entity, partialTicks)
            renderState.baby = entity.isBaby
            model.setAngles(renderState)
            val sleepDirection: Direction? = entity.getSleepingDirection()

            // Interpolate entity position and body rotations.
            val interpolatedEntityPosition: Vec3d = getEntityPositionInterpolated(entity, partialTicks)
                .subtract(camera.pos)
            var interpolatedBodyYaw = MathHelper.lerpAngleDegrees(
                partialTicks, entity.lastBodyYaw,
                entity.bodyYaw
            )
            // Translate by the entity's interpolated position.
            matrixStack.translate(
                interpolatedEntityPosition.getX(), interpolatedEntityPosition.getY(),
                interpolatedEntityPosition.getZ()
            )

            // If entity is sleeping, move their render position by their sleeping offset.
            if (entity.isInPose(EntityPose.SLEEPING) && sleepDirection != null) {
                val sleepingEyeHeight = entity.getEyeHeight(EntityPose.STANDING) - 0.1f
                matrixStack.translate(
                    -sleepDirection.offsetX * sleepingEyeHeight, 0.0f,
                    -sleepDirection.offsetZ * sleepingEyeHeight
                )
            }

            // Scale by the entity's scale.
            val entityScale = entity.getScale()
            matrixStack.scale(entityScale, entityScale, entityScale)

            // If Entity is frozen (similar to shaking from zombie conversion shakes.)
            if (entity.isFrozen) {
                interpolatedBodyYaw += (cos((entity.age * 3.25) * Math.PI * 0.4f)).toFloat()
            }

            // Rotate entity if they are sleeping.
            if (!entity.isInPose(EntityPose.SLEEPING)) {
                matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f-interpolatedBodyYaw))
            }

            // Check for rotations based off of the entity's state (dead, alive, sleeping,
            // using riptide?, etc...)
            if (entity.deathTime > 0) {
                var dyingAngle = MathHelper.sqrt((entity.deathTime + partialTicks - 1.0f) / 20.0f * 1.6f)
                if (dyingAngle > 1.0f) {
                    dyingAngle = 1.0f
                }

                matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(dyingAngle * 90f))
            } else if (entity.isUsingRiptide) {
                matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f - entity.pitch))
                matrixStack
                    .multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.age + partialTicks) * -75.0f))
            } else if (entity.isInPose(EntityPose.SLEEPING)) {
                val sleepAngle = if (sleepDirection != null) getYaw(sleepDirection) else interpolatedBodyYaw
                matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sleepAngle))
                matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0f))
                matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(270.0f))
            }

            // Apply offset for correct rendering on screen. (Not sure why though!)
            matrixStack.scale(-1.0f, -1.0f, 1.0f)
            matrixStack.translate(0.0f, -1.501f, 0.0f)
            val vertexConsumerProvider: Immediate = mc.bufferBuilders.entityVertexConsumers
            val layer: RenderLayer? = RenderLayers.ESP_QUADS
            val bufferBuilder = vertexConsumerProvider.getBuffer(layer)
            model.render(matrixStack, bufferBuilder, 0, 0, color.rgb)
            vertexConsumerProvider.draw(layer)
            matrixStack.pop()
        }
    }


    fun getEntityPositionInterpolated(entity: Entity, delta: Float): Vec3d {
        return Vec3d(
            MathHelper.lerp(delta.toDouble(), entity.lastX, entity.x),
            MathHelper.lerp(delta.toDouble(), entity.lastY, entity.y),
            MathHelper.lerp(delta.toDouble(), entity.lastZ, entity.z)
        )
    }

    private fun getYaw(direction: Direction): Float {
        return when (direction) {
            Direction.WEST -> 0.0f
            Direction.SOUTH -> 90.0f
            Direction.EAST -> 180.0f
            Direction.NORTH -> 270.0f
            else -> 0.0f
        }
    }


}