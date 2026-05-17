package gobby.utils.render

import gobby.mixin.accessor.RenderTypeAccessor
//? if <=1.21.10 {
import gobby.mixin.accessor.CompositeStateBuilderAccessor
import gobby.mixin.accessor.RenderStateShardAccessor
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import java.util.OptionalDouble
//?}
//? if >=1.21.11 {
/*import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes*/
//?}

object ItemBlockRenderTypes {

    //? if <=1.21.10 {
    private fun buildState(lineWidth: Double?, affectsOutline: Boolean = false): RenderType.CompositeState {
        val builder = RenderType.CompositeState.builder() as CompositeStateBuilderAccessor
        if (lineWidth != null) builder.invokeSetLineState(RenderStateShard.LineStateShard(OptionalDouble.of(lineWidth)))
        builder.invokeSetLayeringState(RenderStateShardAccessor.getViewOffsetZLayering())
        builder.invokeSetOutputState(RenderStateShardAccessor.getItemEntityTarget())
        return builder.invokeCreateCompositeState(affectsOutline)
    }

    val ESP_QUADS: RenderType = RenderTypeAccessor.invokeCreate("gobbyclient:esp_quads", 2000, GobbyRenderPipelines.ESP_QUADS, buildState(null))
    val ESP_LINES: RenderType = RenderTypeAccessor.invokeCreate("gobbyclient:esp_lines", 1536, GobbyRenderPipelines.ESP_LINES, buildState(3.0))
    val DEPTH_QUADS: RenderType = RenderTypeAccessor.invokeCreate("gobbyclient:depth_quads", 2000, GobbyRenderPipelines.DEPTH_QUADS, buildState(null))
    val DEPTH_LINES: RenderType = RenderTypeAccessor.invokeCreate("gobbyclient:depth_lines", 1536, GobbyRenderPipelines.DEPTH_LINES, buildState(3.0))

    val TRIS_SIMPLE: RenderType = RenderType.debugTriangleFan()
    val QUADS_SIMPLE: RenderType = RenderType.debugQuads()
    val LINES_SIMPLE: RenderType = RenderType.lines()
    //?}

    //? if >=1.21.11 {
    /*private fun build(name: String, pipeline: com.mojang.blaze3d.pipeline.RenderPipeline, bufferSize: Int): RenderType = RenderTypeAccessor.invokeCreate(
        name,
        RenderSetup.builder(pipeline)
            .bufferSize(bufferSize)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
            .createRenderSetup()
    )

    val ESP_QUADS: RenderType = build("gobbyclient:esp_quads", GobbyRenderPipelines.ESP_QUADS, 2000)
    val ESP_LINES: RenderType = build("gobbyclient:esp_lines", GobbyRenderPipelines.ESP_LINES, 1536)
    val DEPTH_QUADS: RenderType = build("gobbyclient:depth_quads", GobbyRenderPipelines.DEPTH_QUADS, 2000)
    val DEPTH_LINES: RenderType = build("gobbyclient:depth_lines", GobbyRenderPipelines.DEPTH_LINES, 1536)

    val TRIS_SIMPLE: RenderType = RenderTypes.debugTriangleFan()
    val QUADS_SIMPLE: RenderType = RenderTypes.debugQuads()
    val LINES_SIMPLE: RenderType = RenderTypes.lines()*/
    //?}
}
