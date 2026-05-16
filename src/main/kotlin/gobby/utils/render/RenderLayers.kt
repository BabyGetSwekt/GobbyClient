package gobby.utils.render

import gobby.mixin.accessor.CompositeStateBuilderAccessor
import gobby.mixin.accessor.RenderStateShardAccessor
import gobby.mixin.accessor.RenderTypeAccessor
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import java.util.OptionalDouble

object ItemBlockRenderTypes {

    private fun buildState(lineWidth: Double?, affectsOutline: Boolean = false): RenderType.CompositeState {
        val builder = RenderType.CompositeState.builder() as CompositeStateBuilderAccessor
        if (lineWidth != null) builder.invokeSetLineState(RenderStateShard.LineStateShard(OptionalDouble.of(lineWidth)))
        builder.invokeSetLayeringState(RenderStateShardAccessor.getViewOffsetZLayering())
        builder.invokeSetOutputState(RenderStateShardAccessor.getItemEntityTarget())
        return builder.invokeCreateCompositeState(affectsOutline)
    }

    val ESP_QUADS: RenderType = RenderTypeAccessor.invokeCreate(
        "gobbyclient:esp_quads", 2000, GobbyRenderPipelines.ESP_QUADS, buildState(null)
    )

    val ESP_LINES: RenderType = RenderTypeAccessor.invokeCreate(
        "gobbyclient:esp_lines", 1536, GobbyRenderPipelines.ESP_LINES, buildState(3.0)
    )

    val DEPTH_QUADS: RenderType = RenderTypeAccessor.invokeCreate(
        "gobbyclient:depth_quads", 2000, GobbyRenderPipelines.DEPTH_QUADS, buildState(null)
    )

    val DEPTH_LINES: RenderType = RenderTypeAccessor.invokeCreate(
        "gobbyclient:depth_lines", 1536, GobbyRenderPipelines.DEPTH_LINES, buildState(3.0)
    )

    val TRIS_SIMPLE: RenderType = RenderType.debugTriangleFan()
    val QUADS_SIMPLE: RenderType = RenderType.debugQuads()
    val LINES_SIMPLE: RenderType = RenderType.lines()
}
