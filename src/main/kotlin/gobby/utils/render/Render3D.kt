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
        val leRenderer = renderer as LivingEntityRenderer<LivingEntity, LivingEntityRenderState, EntityModel<LivingEntityRenderState>>

        matrixStack.pushPose()

        val model = leRenderer.model
        val renderState = leRenderer.createRenderState(entity, partialTicks)
        renderState.isBaby = entity.isBaby
        model.setupAnim(renderState)
        val sleepDirection = entity.bedOrientation
        val leAccessor = leRenderer as LivingEntityRendererAccessor

        val interpolatedPos = getEntityPositionInterpolated(entity, partialTicks)
            .add(leRenderer.getRenderOffset(renderState))
            .subtract(camera.cameraPos)
        matrixStack.translate(interpolatedPos.x, interpolatedPos.y, interpolatedPos.z)

        if (entity.hasPose(Pose.SLEEPING) && sleepDirection != null) {
            val sleepingEyeHeight = entity.getEyeHeight(Pose.STANDING) - 0.1f
            matrixStack.translate(
                -sleepDirection.stepX * sleepingEyeHeight,
                0.0f,
                -sleepDirection.stepZ * sleepingEyeHeight
            )
        }

        val entityScale = renderState.scale
        matrixStack.scale(entityScale, entityScale, entityScale)
        leAccessor.`gobbyclient$invokeSetupRotations`(renderState, matrixStack, renderState.bodyRot, entityScale)
        matrixStack.scale(-1.0f, -1.0f, 1.0f)
        leAccessor.`gobbyclient$invokeScale`(renderState, matrixStack)
        matrixStack.translate(0.0f, -1.501f, 0.0f)

        val solidFill = color.rgb or OPAQUE_ALPHA
        collector.submitModel(
            model,
            renderState,
            matrixStack,
            ItemBlockRenderTypes.ESP_QUADS,
            BAKED_LIGHT_IGNORED,
            OverlayTexture.NO_OVERLAY,
            solidFill,
            null,
            EntityRenderState.NO_OUTLINE,
            null
        )

        if (renderArmor && renderState is HumanoidRenderState) {
            armorLayerOf(leRenderer)?.submit(
                matrixStack,
                TintingSubmitCollector(collector, solidFill),
                BAKED_LIGHT_IGNORED,
                renderState,
                renderState.yRot,
                renderState.xRot
            )
        }
        matrixStack.popPose()
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
