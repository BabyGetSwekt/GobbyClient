package gobby.utils.render

//? if >26.1.2
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.DefaultVertexFormat
//? if <=26.1.2
/*import com.mojang.blaze3d.vertex.VertexFormat*/
import gobby.mixin.accessor.RenderPipelinesAccessor
//? if >26.1.2
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.resources.Identifier as ResourceLocation
import java.util.Optional

object GobbyRenderPipelines {

    private fun base(builder: RenderPipeline.Builder): RenderPipeline.Builder = builder
        //? if >26.1.2 {
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        //?}
        .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)

    val ESP_QUADS: RenderPipeline = RenderPipelinesAccessor.invokeRegister(
        base(RenderPipeline.builder())
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            //? if >26.1.2 {
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            //?}
            //? if <=26.1.2
            /*.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)*/
            .withDepthStencilState(Optional.empty())
            .withLocation(ResourceLocation.fromNamespaceAndPath("gobbyclient", "pipeline/esp_quads"))
            .build()
    )

    val ESP_LINES: RenderPipeline = RenderPipelinesAccessor.invokeRegister(
        base(RenderPipeline.builder())
            //? if >26.1.2
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/rendertype_lines")
            //? if >26.1.2 {
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
            .withPrimitiveTopology(PrimitiveTopology.LINES)
            //?}
            //? if <=26.1.2
            /*.withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES)*/
            .withDepthStencilState(Optional.empty())
            .withLocation(ResourceLocation.fromNamespaceAndPath("gobbyclient", "pipeline/esp_lines"))
            .build()
    )

    val DEPTH_QUADS: RenderPipeline = RenderPipelinesAccessor.invokeRegister(
        base(RenderPipeline.builder())
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            //? if >26.1.2 {
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            //?}
            //? if <=26.1.2
            /*.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)*/
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withLocation(ResourceLocation.fromNamespaceAndPath("gobbyclient", "pipeline/depth_quads"))
            .build()
    )

    val DEPTH_LINES: RenderPipeline = RenderPipelinesAccessor.invokeRegister(
        base(RenderPipeline.builder())
            //? if >26.1.2
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/rendertype_lines")
            //? if >26.1.2 {
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
            .withPrimitiveTopology(PrimitiveTopology.LINES)
            //?}
            //? if <=26.1.2
            /*.withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES)*/
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withLocation(ResourceLocation.fromNamespaceAndPath("gobbyclient", "pipeline/depth_lines"))
            .build()
    )
}
