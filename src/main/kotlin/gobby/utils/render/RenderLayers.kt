package gobby.utils.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import gobby.mixin.accessor.RenderTypeAccessor
import net.minecraft.client.renderer.rendertype.*

object ItemBlockRenderTypes {

    private fun buildQuads(name: String, pipeline: RenderPipeline): RenderType = RenderTypeAccessor.invokeCreate(
        name,
        RenderSetup.builder(pipeline)
            .sortOnUpload()
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup()
    )

    private fun buildLines(name: String, pipeline: RenderPipeline): RenderType = RenderTypeAccessor.invokeCreate(
        name,
        RenderSetup.builder(pipeline)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
            .createRenderSetup()
    )

    val ESP_QUADS: RenderType = buildQuads("gobbyclient:esp_quads", GobbyRenderPipelines.ESP_QUADS)
    val ESP_LINES: RenderType = buildLines("gobbyclient:esp_lines", GobbyRenderPipelines.ESP_LINES)
    val DEPTH_QUADS: RenderType = buildQuads("gobbyclient:depth_quads", GobbyRenderPipelines.DEPTH_QUADS)
    val DEPTH_LINES: RenderType = buildLines("gobbyclient:depth_lines", GobbyRenderPipelines.DEPTH_LINES)

    val TRIS_SIMPLE: RenderType = RenderTypes.debugTriangleFan()
    val QUADS_SIMPLE: RenderType = RenderTypes.debugQuads()
    val LINES_SIMPLE: RenderType = RenderTypes.lines()
}
