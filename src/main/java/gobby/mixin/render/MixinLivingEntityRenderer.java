package gobby.mixin.render;

import gobby.interfaces.EspLayerHidingState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {

    @Inject(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$hideLayersForChams(LivingEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
        if (((EspLayerHidingState) state).gobbyclient$shouldHideLayers()) cir.setReturnValue(false);
    }

    @Inject(method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$hideBodyForChams(LivingEntityRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing, CallbackInfoReturnable<RenderType> cir) {
        if (((EspLayerHidingState) state).gobbyclient$shouldHideBody()) cir.setReturnValue(null);
    }
}
