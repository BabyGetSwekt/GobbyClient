package gobby.utils.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import gobby.mixin.accessor.RenderPipelinesAccessor
import net.minecraft.resources.ResourceLocation

object GobbyRenderPipelines {

    private fun base(builder: RenderPipeline.Builder): RenderPipeline.Builder = builder
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withUniform("Fog", UniformType.UNIFORM_BUFFER)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)

    val ESP_QUADS: RenderPipeline = RenderPipelinesAccessor.invokeRegister(
        base(RenderPipeline.builder())
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withLocation(ResourceLocation.fromNamespaceAndPath("gobbyclient", "pipeline/esp_quads"))
            .build()
    )

    val ESP_LINES: RenderPipeline = RenderPipelinesAccessor.invokeRegister(
        base(RenderPipeline.builder())
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/rendertype_lines")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withLocation(ResourceLocation.fromNamespaceAndPath("gobbyclient", "pipeline/esp_lines"))
            .build()
    )

    val DEPTH_QUADS: RenderPipeline = RenderPipelinesAccessor.invokeRegister(
        base(RenderPipeline.builder())
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withLocation(ResourceLocation.fromNamespaceAndPath("gobbyclient", "pipeline/depth_quads"))
            .build()
    )

    val DEPTH_LINES: RenderPipeline = RenderPipelinesAccessor.invokeRegister(
        base(RenderPipeline.builder())
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/rendertype_lines")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withLocation(ResourceLocation.fromNamespaceAndPath("gobbyclient", "pipeline/depth_lines"))
            .build()
    )
}
