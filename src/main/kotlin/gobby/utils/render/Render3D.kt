package gobby.utils.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import gobby.Gobbyclient.Companion.mc
import gobby.events.render.NewRender3DEvent
import gobby.utils.Utils.cameraPos
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Camera
import net.minecraft.client.model.EntityModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.cos

/**
 * Contents of this file are based on Aoba-Client and the work of coltonk9043 under GNU General Public License v3.0.
 * All credits go to him.
 * @author coltonk9043 (https://github.com/coltonk9043)
 * License: https://github.com/coltonk9043/Aoba-Client/blob/master/LICENSE
 */
object Render3D {

    private var submitNodeCollector: SubmitNodeCollector? = null

    init {
        LevelRenderEvents.COLLECT_SUBMITS.register { context -> submitNodeCollector = context.submitNodeCollector() }
    }

    fun NewRender3DEvent.drawEntityModel(
        matrixStack: PoseStack,
        camera: Camera,
        partialTicks: Float,
        entity: Entity?,
        color: Color
    ) {
        if (entity !is LivingEntity) return
        val renderer = mc.entityRenderDispatcher.getRenderer(entity) ?: return

        @Suppress("UNCHECKED_CAST")
        val leRenderer = renderer as LivingEntityRenderer<LivingEntity, LivingEntityRenderState, EntityModel<LivingEntityRenderState>>

        matrixStack.pushPose()

        val model = leRenderer.model
        val renderState = leRenderer.createRenderState(entity, partialTicks)
        renderState.isBaby = entity.isBaby
        model.setupAnim(renderState)
        val sleepDirection = entity.bedOrientation

        val interpolatedPos = getEntityPositionInterpolated(entity, partialTicks).subtract(camera.cameraPos)
        var interpolatedBodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot)
        matrixStack.translate(interpolatedPos.x, interpolatedPos.y, interpolatedPos.z)

        if (entity.hasPose(Pose.SLEEPING) && sleepDirection != null) {
            val sleepingEyeHeight = entity.getEyeHeight(Pose.STANDING) - 0.1f
            matrixStack.translate(
                -sleepDirection.stepX * sleepingEyeHeight,
                0.0f,
                -sleepDirection.stepZ * sleepingEyeHeight
            )
        }

        val entityScale = entity.scale
        matrixStack.scale(entityScale, entityScale, entityScale)

        if (entity.isFullyFrozen) {
            interpolatedBodyYaw += cos((entity.tickCount * 3.25) * Math.PI * 0.4f).toFloat()
        }

        if (!entity.hasPose(Pose.SLEEPING)) {
            matrixStack.mulPose(Axis.YP.rotationDegrees(180f - interpolatedBodyYaw))
        }

        if (entity.deathTime > 0) {
            var dyingAngle = Mth.sqrt((entity.deathTime + partialTicks - 1.0f) / 20.0f * 1.6f)
            if (dyingAngle > 1.0f) dyingAngle = 1.0f
            matrixStack.mulPose(Axis.ZP.rotationDegrees(dyingAngle * 90f))
        } else if (entity.isAutoSpinAttack) {
            matrixStack.mulPose(Axis.XP.rotationDegrees(-90.0f - entity.xRot))
            matrixStack.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTicks) * -75.0f))
        } else if (entity.hasPose(Pose.SLEEPING)) {
            val sleepAngle = sleepDirection?.let { yawFromDirection(it) } ?: interpolatedBodyYaw
            matrixStack.mulPose(Axis.YP.rotationDegrees(sleepAngle))
            matrixStack.mulPose(Axis.ZP.rotationDegrees(90.0f))
            matrixStack.mulPose(Axis.YP.rotationDegrees(270.0f))
        }

        val customName = entity.customName?.string
        if (customName != null && customName.contains("Dinnerbone")) {
            matrixStack.translate(0.0f, entity.bbHeight + 0.1f, 0.0f)
            matrixStack.mulPose(Axis.ZP.rotationDegrees(180.0f))
        }

        matrixStack.scale(-1.0f, -1.0f, 1.0f)
        matrixStack.translate(0.0f, -1.501f, 0.0f)

        submitNodeCollector?.submitModel(model, renderState, matrixStack, ItemBlockRenderTypes.ESP_QUADS, 0, 0, color.rgb, null)
        matrixStack.popPose()
    }

    private fun yawFromDirection(direction: Direction): Float = when (direction) {
        Direction.WEST -> 0.0f
        Direction.SOUTH -> 90.0f
        Direction.EAST -> 180.0f
        Direction.NORTH -> 270.0f
        else -> 0.0f
    }

    fun getEntityPositionInterpolated(entity: Entity, delta: Float): Vec3 {
        return Vec3(
            Mth.lerp(delta.toDouble(), entity.xOld, entity.x),
            Mth.lerp(delta.toDouble(), entity.yOld, entity.y),
            Mth.lerp(delta.toDouble(), entity.zOld, entity.z)
        )
    }
}
