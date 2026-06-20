package gobby.utils.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.Model
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureAtlasSprite

class TintingSubmitCollector(
    private val delegate: SubmitNodeCollector,
    private val tint: Int
) : SubmitNodeCollector by delegate {

    override fun order(order: Int): OrderedSubmitNodeCollector = this

    override fun <S : Any> submitModel(
        model: Model<in S>,
        state: S,
        pose: PoseStack,
        renderType: RenderType,
        light: Int,
        overlay: Int,
        color: Int,
        sprite: TextureAtlasSprite?,
        outline: Int,
        crumbling: CrumblingOverlay?
    ) = delegate.submitModel(
        model,
        state,
        pose,
        ItemBlockRenderTypes.ESP_QUADS,
        light,
        OverlayTexture.NO_OVERLAY,
        tint,
        null as TextureAtlasSprite?,
        EntityRenderState.NO_OUTLINE,
        null
    )
}
