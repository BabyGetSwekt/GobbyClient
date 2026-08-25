package gobby.mixin;

import gobby.features.skyblock.FreeCam;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import gobby.pathfinder.etherwarp.EtherwarpPathExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void gobbyclient$onCameraUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
        gobbyclient$applyFreeCam();
    }

    @Unique
    private void gobbyclient$applyFreeCam() {
        if (!FreeCam.INSTANCE.getEnabled()) return;
        FreeCam.INSTANCE.updateMovement();
        setPosition(FreeCam.INSTANCE.getCamX(), FreeCam.INSTANCE.getCamY(), FreeCam.INSTANCE.getCamZ());
        setRotation(FreeCam.INSTANCE.getCamYaw(), FreeCam.INSTANCE.getCamPitch());
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void gobbyclient$applySmoothedPlayerPosition(DeltaTracker deltaTracker, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        Camera camera = (Camera) (Object) this;
        if (player == null || camera.entity() != player) return;
        Vec3 smoothed = EtherwarpPathExecutor.INSTANCE.smoothedRenderPosition(player.position(), System.nanoTime());
        if (smoothed == null) return;
        setPosition(camera.position().add(smoothed.subtract(player.position())));
    }

    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$isThirdPerson(CallbackInfoReturnable<Boolean> cir) {
        if (FreeCam.INSTANCE.getEnabled()) {
            cir.setReturnValue(true);
        }
    }
}
