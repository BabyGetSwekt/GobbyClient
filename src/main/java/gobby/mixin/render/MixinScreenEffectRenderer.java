package gobby.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import gobby.features.render.NoBlockOverlay;
import gobby.features.render.NoFire;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class MixinScreenEffectRenderer {

    @Inject(method = "submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V", at = @At("HEAD"), cancellable = true)
    private static void gobbyclient$cancelFireOverlay(PoseStack matrices, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (NoFire.INSTANCE.getEnabled()) ci.cancel();
    }

    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void gobbyclient$cancelBlockOverlay(CallbackInfo ci) {
        if (NoBlockOverlay.INSTANCE.getEnabled()) ci.cancel();
    }
}
