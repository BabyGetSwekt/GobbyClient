package gobby.mixin.render;

import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.resource.SplashTextResourceSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SplashTextResourceSupplier.class)
public class MixinSplashTextResourceSupplier {

    @Inject(method = "get()Lnet/minecraft/client/gui/screen/SplashTextRenderer;", at = @At("HEAD"), cancellable = true)
    public void gobbyclient$getSplashText(CallbackInfoReturnable<SplashTextRenderer> cir) {
        cir.setReturnValue(new SplashTextRenderer("Man, I fucking love Gobby Client!"));
    }
}
