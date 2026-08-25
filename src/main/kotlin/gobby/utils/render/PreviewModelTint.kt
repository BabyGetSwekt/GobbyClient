package gobby.utils.render

import com.mojang.blaze3d.vertex.PoseStack
import gobby.Gobbyclient.Companion.mc
import gobby.mixin.accessor.LivingEntityRendererAccessor
import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.LivingEntity
import java.awt.Color

private const val BAKED_LIGHT_IGNORED = 0
private const val MODEL_ORIGIN_OFFSET = -1.501f
private const val FLIP = -1.0f
private const val STRIDE = 0.32f
private const val LEVEL = 0f

object PreviewModelTint {

    private var target: LivingEntityRenderState? = null
    private var subject: LivingEntity? = null
    private var tint = 0
    var suppressBob = false
        private set

    fun expect(state: LivingEntityRenderState, entity: LivingEntity, color: Color) {
        target = state
        subject = entity
        tint = color.rgb
    }

    fun clear() {
        target = null
        subject = null
    }

    fun beginFrame(state: EntityRenderState) {
        suppressBob = state === target
    }

    fun endFrame() {
        suppressBob = false
    }

    fun poseForPreview(model: HumanoidModel<*>) {
        if (!suppressBob) return
        listOf(model.leftArm, model.rightArm).forEach {
            it.yRot = LEVEL
            it.zRot = LEVEL
        }
        model.rightLeg.xRot = STRIDE
        model.leftLeg.xRot = -STRIDE
        listOf(model.leftLeg, model.rightLeg).forEach {
            it.yRot = LEVEL
            it.zRot = LEVEL
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun submit(state: EntityRenderState, poseStack: PoseStack, collector: SubmitNodeCollector) {
        if (state !== target) return
        val entity = subject ?: return
        val renderer = mc.entityRenderDispatcher.getRenderer(entity) as? LivingEntityRenderer<LivingEntity, LivingEntityRenderState, EntityModel<LivingEntityRenderState>> ?: return
        val living = state as? LivingEntityRenderState ?: return

        val model = renderer.model
        model.setupAnim(living)
        val accessor = renderer as LivingEntityRendererAccessor

        poseStack.pushPose()
        accessor.`gobbyclient$invokeSetupRotations`(living, poseStack, living.bodyRot, living.scale)
        poseStack.scale(FLIP, FLIP, 1.0f)
        accessor.`gobbyclient$invokeScale`(living, poseStack)
        poseStack.translate(0.0f, MODEL_ORIGIN_OFFSET, 0.0f)
        collector.submitModel(
            model, living, poseStack, ItemBlockRenderTypes.ESP_QUADS, BAKED_LIGHT_IGNORED,
            OverlayTexture.NO_OVERLAY, tint, null, EntityRenderState.NO_OUTLINE, null
        )
        poseStack.popPose()
    }
}
