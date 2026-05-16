package gobby.mixin.render;

import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
//? if >=1.21.11
/*import net.minecraft.network.chat.Component;*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SplashManager.class)
public class MixinSplashManager {

    @Inject(method = "getSplash()Lnet/minecraft/client/gui/components/SplashRenderer;", at = @At("HEAD"), cancellable = true)
    public void gobbyclient$getSplashText(CallbackInfoReturnable<SplashRenderer> cir) {
        //? if <=1.21.10
        cir.setReturnValue(new SplashRenderer("Jaminul stinks!"));
        //? if >=1.21.11
        /*cir.setReturnValue(new SplashRenderer(Component.literal("Jaminul stinks!")));*/
    }
}
