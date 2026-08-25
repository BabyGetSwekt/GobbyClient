package gobby.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import gobby.utils.render.PreviewModelTint;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiEntityRenderer.class)
public class MixinGuiEntityRenderer {

    @Inject(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("HEAD"))
    private void gobbyclient$beginPreviewFrame(GuiEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        PreviewModelTint.INSTANCE.beginFrame(state.renderState());
    }

    @Inject(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("TAIL"))
    private void gobbyclient$submitPreviewTint(GuiEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        PreviewModelTint.INSTANCE.submit(state.renderState(), poseStack, collector);
        PreviewModelTint.INSTANCE.endFrame();
    }
}
