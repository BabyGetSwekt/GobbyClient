package gobby.mixin.player;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static gobby.features.developer.PacketDebug.charging;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

//    @Inject(method = "isUsingItem()Z", at = @At("HEAD"), cancellable = true)
//    private void gobbyclient$usingItem(CallbackInfoReturnable<Boolean> cir) {
//        if (charging) {
//            cir.setReturnValue(true);
//        }
//    }
//
//    @Inject(method = "getActiveHand()Lnet/minecraft/util/Hand;", at = @At("HEAD"), cancellable = true)
//    private void gobbyclient$activeHand(CallbackInfoReturnable<net.minecraft.util.Hand> cir) {
//        if (charging) {
//            cir.setReturnValue(net.minecraft.util.Hand.MAIN_HAND);
//        }
//    }
//
//    @Inject(method = "tickActiveItemStack()V", at = @At("HEAD"), cancellable = true)
//    private void gobbyclient$tickActiveItemStack(CallbackInfo ci) {
//        if (charging) {
//            ci.cancel();
//        }
//    }
}
