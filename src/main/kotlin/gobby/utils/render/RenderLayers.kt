package gobby.utils.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.minecraft.client.render.RenderLayer
//? if <=1.21.10 {
import net.minecraft.client.render.RenderPhase
import java.util.OptionalDouble
//?}
//? if >=1.21.11 {
/*import net.minecraft.client.render.LayeringTransform
import net.minecraft.client.render.OutputTarget
import net.minecraft.client.render.RenderSetup
import net.minecraft.client.render.RenderLayers as McRenderLayers*/
//?}


object RenderLayers {

    //? if <=1.21.10 {
    val ESP_QUADS: RenderLayer = RenderLayer.of(
        "gobby:esp_quads", 2000, GobbyRenderPipelines.ESP_QUADS, RenderLayer.MultiPhaseParameters.builder()
            .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
            .target(RenderPhase.ITEM_ENTITY_TARGET)
            .build(false)
    )

    val ESP_LINES: RenderLayer = RenderLayer.of(
        "gobby:esp_lines", 1536, GobbyRenderPipelines.ESP_TEST_LINES,
        RenderLayer.MultiPhaseParameters.builder()
            .lineWidth(RenderPhase.LineWidth(OptionalDouble.of(3.0)))
            .layering(RenderLayer.VIEW_OFFSET_Z_LAYERING)
            .target(RenderLayer.ITEM_ENTITY_TARGET)
            .build(false)
    )

    val DEPTH_QUADS: RenderLayer = RenderLayer.of(
        "gobby:depth_quads", 2000, GobbyRenderPipelines.DEPTH_QUADS, RenderLayer.MultiPhaseParameters.builder()
            .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
            .target(RenderPhase.ITEM_ENTITY_TARGET)
            .build(false)
    )

    val DEPTH_LINES: RenderLayer = RenderLayer.of(
        "gobby:depth_lines", 1536, GobbyRenderPipelines.DEPTH_LINES,
        RenderLayer.MultiPhaseParameters.builder()
            .lineWidth(RenderPhase.LineWidth(OptionalDouble.of(3.0)))
            .layering(RenderLayer.VIEW_OFFSET_Z_LAYERING)
            .target(RenderLayer.ITEM_ENTITY_TARGET)
            .build(false)
    )

    val TRIS_SIMPLE: RenderLayer = RenderLayer.getDebugTriangleFan()
    val QUADS_SIMPLE: RenderLayer = RenderLayer.getDebugQuads()
    val LINES_SIMPLE: RenderLayer = RenderLayer.getLines()
    //?}

    //? if >=1.21.11 {
    /*private fun layered(name: String, pipeline: RenderPipeline, bufferSize: Int): RenderLayer = RenderLayer.of(
        name,
        RenderSetup.builder(pipeline)
            .expectedBufferSize(bufferSize)
            .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
            .build()
    )

    val ESP_QUADS: RenderLayer = layered("gobby:esp_quads", GobbyRenderPipelines.ESP_QUADS, 2000)
    val ESP_LINES: RenderLayer = layered("gobby:esp_lines", GobbyRenderPipelines.ESP_TEST_LINES, 1536)
    val DEPTH_QUADS: RenderLayer = layered("gobby:depth_quads", GobbyRenderPipelines.DEPTH_QUADS, 2000)
    val DEPTH_LINES: RenderLayer = layered("gobby:depth_lines", GobbyRenderPipelines.DEPTH_LINES, 1536)

    val TRIS_SIMPLE: RenderLayer = McRenderLayers.LINES
    val QUADS_SIMPLE: RenderLayer = McRenderLayers.LINES
    val LINES_SIMPLE: RenderLayer = McRenderLayers.LINES*/
    //?}
}
