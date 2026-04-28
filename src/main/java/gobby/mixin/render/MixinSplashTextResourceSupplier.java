package gobby.mixin.render;

import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.resource.SplashTextResourceSupplier;
//? if >=1.21.11
/*import net.minecraft.text.Text;*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SplashTextResourceSupplier.class)
public class MixinSplashTextResourceSupplier {

    @Inject(method = "get()Lnet/minecraft/client/gui/screen/SplashTextRenderer;", at = @At("HEAD"), cancellable = true)
    public void gobbyclient$getSplashText(CallbackInfoReturnable<SplashTextRenderer> cir) {
        //? if <=1.21.10
        cir.setReturnValue(new SplashTextRenderer("Jaminul stinks!"));
        //? if >=1.21.11
        /*cir.setReturnValue(new SplashTextRenderer(Text.literal("Jaminul stinks!")));*/
    }
}
