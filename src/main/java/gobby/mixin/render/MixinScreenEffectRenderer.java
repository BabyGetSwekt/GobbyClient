package gobby.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import gobby.features.render.NoBlockOverlay;
import gobby.features.render.NoFire;
//? if <=26.1.2
/*import net.minecraft.client.renderer.MultiBufferSource;*/
import net.minecraft.client.renderer.ScreenEffectRenderer;
//? if >26.1.2
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class MixinScreenEffectRenderer {

    //? if >26.1.2 {
    @Inject(method = "submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V", at = @At("HEAD"), cancellable = true)
    private static void gobbyclient$cancelFireOverlay(PoseStack matrices, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (NoFire.INSTANCE.getEnabled()) ci.cancel();
    }
    //?}
    //? if <=26.1.2 {
    /*@Inject(method = "renderFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V", at = @At("HEAD"), cancellable = true)
    private static void gobbyclient$cancelFireOverlay(PoseStack matrices, MultiBufferSource bufferSource, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (NoFire.INSTANCE.getEnabled()) ci.cancel();
    }
    *///?}

    //? if >26.1.2
    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    //? if <=26.1.2
    /*@Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)*/
    private static void gobbyclient$cancelBlockOverlay(CallbackInfo ci) {
        if (NoBlockOverlay.INSTANCE.getEnabled()) ci.cancel();
    }
}
