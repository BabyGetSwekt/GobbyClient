package gobby.mixin;

import gobby.features.render.SkinChanger;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class MixinAbstractClientPlayer {

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$skinChanger(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin custom = SkinChanger.INSTANCE.getSkinFor((AbstractClientPlayer) (Object) this);
        if (custom != null) cir.setReturnValue(custom);
    }
}
