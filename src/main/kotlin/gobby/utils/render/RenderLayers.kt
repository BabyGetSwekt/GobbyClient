package gobby.utils.render

import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderPhase
import java.util.OptionalDouble


object RenderLayers {


    val ESP_QUADS: RenderLayer.MultiPhase = RenderLayer.of(
        "gobby:esp_quads", 2000, GobbyRenderPipelines.ESP_QUADS, RenderLayer.MultiPhaseParameters.builder()
            .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
            .target(RenderPhase.ITEM_ENTITY_TARGET)
            .build(false)
    )

    val ESP_LINES: RenderLayer.MultiPhase = RenderLayer.of(
        "gobby:esp_lines", 1536, GobbyRenderPipelines.ESP_TEST_LINES,
        RenderLayer.MultiPhaseParameters.builder()
            .lineWidth(RenderPhase.LineWidth(OptionalDouble.of(3.0)))
            .layering(RenderLayer.VIEW_OFFSET_Z_LAYERING)
            .target(RenderLayer.ITEM_ENTITY_TARGET)
            .build(false)
    )


//    val ESP_LINES: RenderLayer.MultiPhase = RenderLayer.of(
//        "gobby:esp_lines", 2000, GobbyRenderPipelines.ESP_LINES,
//        RenderLayer.MultiPhaseParameters.builder()
//            .lineWidth(RenderPhase.LineWidth(OptionalDouble.of(1.0)))
//            .layering(RenderLayer.VIEW_OFFSET_Z_LAYERING)
//            .target(RenderLayer.ITEM_ENTITY_TARGET).build(false)
//    )

    val TRIS_SIMPLE: RenderLayer = RenderLayer.getDebugTriangleFan()
    val QUADS_SIMPLE: RenderLayer = RenderLayer.getDebugQuads()

    val LINES_SIMPLE: RenderLayer = RenderLayer.getLines()
}