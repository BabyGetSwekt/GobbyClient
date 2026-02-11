package gobby.mixin.player;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static gobby.features.developer.PacketDebug.charging;

@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {

//    @Shadow
//    private boolean usingItem;
//
//    @Inject(method = "isUsingItem()Z", at = @At("HEAD"), cancellable = true)
//    private void gobbyclient$usingItem(CallbackInfoReturnable<Boolean> cir) {
//        if (charging || usingItem) {
//            cir.setReturnValue(true);
//        }
//    }
}
