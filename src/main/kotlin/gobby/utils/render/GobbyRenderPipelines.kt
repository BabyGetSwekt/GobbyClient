package gobby.utils.render

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import gobby.mixin.accessor.RenderPipelinesAccessor
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.resources.Identifier as ResourceLocation
import java.util.Optional

object GobbyRenderPipelines {

    private fun base(builder: RenderPipeline.Builder): RenderPipeline.Builder = builder
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)

    val ESP_QUADS: RenderPipeline = RenderPipelinesAccessor.invokeRegister(
        base(RenderPipeline.builder())
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(Optional.empty())
            .withLocation(ResourceLocation.fromNamespaceAndPath("gobbyclient", "pipeline/esp_quads"))
            .build()
    )

    val ESP_LINES: RenderPipeline = RenderPipelinesAccessor.invokeRegister(
        base(RenderPipeline.builder())
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/rendertype_lines")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
            .withPrimitiveTopology(PrimitiveTopology.LINES)
            .withDepthStencilState(Optional.empty())
            .withLocation(ResourceLocation.fromNamespaceAndPath("gobbyclient", "pipeline/esp_lines"))
            .build()
    )

    val DEPTH_QUADS: RenderPipeline = RenderPipelinesAccessor.invokeRegister(
        base(RenderPipeline.builder())
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withLocation(ResourceLocation.fromNamespaceAndPath("gobbyclient", "pipeline/depth_quads"))
            .build()
    )

    val DEPTH_LINES: RenderPipeline = RenderPipelinesAccessor.invokeRegister(
        base(RenderPipeline.builder())
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/rendertype_lines")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
            .withPrimitiveTopology(PrimitiveTopology.LINES)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withLocation(ResourceLocation.fromNamespaceAndPath("gobbyclient", "pipeline/depth_lines"))
            .build()
    )
}
