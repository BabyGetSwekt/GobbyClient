package gobby.mixin;

import gobby.features.skyblock.FreeCam;
import gobby.mixin.accessor.WalkAnimationStateAccessor;
import gobby.utils.managers.PacketOrderManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {

    @Inject(method = "tick", at = @At("RETURN"))
    private void gobbyclient$afterTick(CallbackInfo ci) {
        if (!FreeCam.INSTANCE.getEnabled()) return;
        LocalPlayer self = (LocalPlayer) (Object) this;

        Vec3 vel = self.getDeltaMovement();
        double horizontalSpeed = vel.x * vel.x + vel.z * vel.z;
        if (horizontalSpeed < 0.0001) {
            WalkAnimationStateAccessor limb = (WalkAnimationStateAccessor) self.walkAnimation;
            limb.setSpeed(0f);
            limb.setLastSpeed(0f);
        }
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void gobbyclient$beforeSendMovement(CallbackInfo ci) {
        PacketOrderManager.INSTANCE.execute(PacketOrderManager.Phase.ITEM_USE);
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void gobbyclient$afterSendMovement(CallbackInfo ci) {
        PacketOrderManager.INSTANCE.execute(PacketOrderManager.Phase.AFTER_MOVEMENT);
    }
}
