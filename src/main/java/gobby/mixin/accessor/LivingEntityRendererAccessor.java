package gobby.mixin.accessor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {

    @Accessor("layers")
    List<RenderLayer<?, ?>> gobbyclient$getLayers();

    @Invoker("setupRotations")
    void gobbyclient$invokeSetupRotations(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float scale);

    @Invoker("scale")
    void gobbyclient$invokeScale(LivingEntityRenderState state, PoseStack poseStack);
}
