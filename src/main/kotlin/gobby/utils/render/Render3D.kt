package gobby.utils.render

import com.mojang.blaze3d.vertex.PoseStack
import gobby.Gobbyclient.Companion.mc
import gobby.mixin.accessor.LivingEntityRendererAccessor
import gobby.utils.Utils.cameraPos
import net.minecraft.client.Camera
import net.minecraft.client.model.EntityModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.Vec3
import java.awt.Color

/**
 * Contents of this file are based on Aoba-Client and the work of coltonk9043 under GNU General Public License v3.0.
 * All credits go to him.
 * @author coltonk9043 (https://github.com/coltonk9043)
 * License: https://github.com/coltonk9043/Aoba-Client/blob/master/LICENSE
 */

object Render3D {

    private val OPAQUE_ALPHA = 0xFF shl 24
    private const val BAKED_LIGHT_IGNORED = 0

    fun drawEntityModel(
        matrixStack: PoseStack,
        collector: SubmitNodeCollector,
        camera: Camera,
        partialTicks: Float,
        entity: Entity?,
        color: Color,
        renderArmor: Boolean = false
    ) {
        if (entity !is LivingEntity) return
        val renderer = mc.entityRenderDispatcher.getRenderer(entity) ?: return

        @Suppress("UNCHECKED_CAST")
        val livingRenderer = renderer as LivingEntityRenderer<LivingEntity, LivingEntityRenderState, EntityModel<LivingEntityRenderState>>
        val renderState = livingRenderer.createRenderState(entity, partialTicks)
        renderState.isBaby = entity.isBaby
        matrixStack.pushPose()
        prepareEntityModel(matrixStack, camera, entity, livingRenderer, renderState, partialTicks)
        submitEntityModel(matrixStack, collector, livingRenderer, renderState, color, renderArmor)
        matrixStack.popPose()
    }

    private fun prepareEntityModel(
        matrixStack: PoseStack,
        camera: Camera,
        entity: LivingEntity,
        renderer: LivingEntityRenderer<LivingEntity, LivingEntityRenderState, EntityModel<LivingEntityRenderState>>,
        renderState: LivingEntityRenderState,
        partialTicks: Float
    ) {
        val model = renderer.model
        model.setupAnim(renderState)
        val sleepingDirection = entity.bedOrientation
        val accessor = renderer as LivingEntityRendererAccessor
        val position = getEntityPositionInterpolated(entity, partialTicks).add(renderer.getRenderOffset(renderState)).subtract(camera.cameraPos)
        matrixStack.translate(position.x, position.y, position.z)
        if (entity.hasPose(Pose.SLEEPING) && sleepingDirection != null) {
            val eyeHeight = entity.getEyeHeight(Pose.STANDING) - 0.1f
            matrixStack.translate(-sleepingDirection.stepX * eyeHeight, 0.0f, -sleepingDirection.stepZ * eyeHeight)
        }
        val scale = renderState.scale
        matrixStack.scale(scale, scale, scale)
        accessor.`gobbyclient$invokeSetupRotations`(renderState, matrixStack, renderState.bodyRot, scale)
        matrixStack.scale(-1.0f, -1.0f, 1.0f)
        accessor.`gobbyclient$invokeScale`(renderState, matrixStack)
        matrixStack.translate(0.0f, -1.501f, 0.0f)
    }

    private fun submitEntityModel(
        matrixStack: PoseStack,
        collector: SubmitNodeCollector,
        renderer: LivingEntityRenderer<LivingEntity, LivingEntityRenderState, EntityModel<LivingEntityRenderState>>,
        renderState: LivingEntityRenderState,
        color: Color,
        renderArmor: Boolean
    ) {
        val solidFill = color.rgb or OPAQUE_ALPHA
        collector.submitModel(renderer.model, renderState, matrixStack, ItemBlockRenderTypes.ESP_QUADS, BAKED_LIGHT_IGNORED, OverlayTexture.NO_OVERLAY, solidFill, null, EntityRenderState.NO_OUTLINE, null)
        if (renderArmor && renderState is HumanoidRenderState) {
            armorLayerOf(renderer)?.submit(matrixStack, TintingSubmitCollector(collector, solidFill), BAKED_LIGHT_IGNORED, renderState, renderState.yRot, renderState.xRot)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun armorLayerOf(renderer: LivingEntityRenderer<*, *, *>): HumanoidArmorLayer<HumanoidRenderState, *, *>? =
        (renderer as LivingEntityRendererAccessor).`gobbyclient$getLayers`()
            .firstOrNull { it is HumanoidArmorLayer<*, *, *> } as? HumanoidArmorLayer<HumanoidRenderState, *, *>

    fun getEntityPositionInterpolated(entity: Entity, delta: Float): Vec3 {
        return Vec3(
            Mth.lerp(delta.toDouble(), entity.xOld, entity.x),
            Mth.lerp(delta.toDouble(), entity.yOld, entity.y),
            Mth.lerp(delta.toDouble(), entity.zOld, entity.z)
        )
    }
}

