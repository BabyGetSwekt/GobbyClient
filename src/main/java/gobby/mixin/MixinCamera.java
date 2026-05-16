package gobby.mixin;

import gobby.features.skyblock.FreeCam;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
//? if >=1.21.11
/*import net.minecraft.world.level.Level;*/
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
    protected abstract void setRotation(float yaw, float pitch);

    //? if <=1.21.10 {
    @Inject(method = "setup", at = @At("TAIL"))
    private void gobbyclient$onCameraUpdate(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        gobbyclient$applyFreeCam();
    }
    //?}
    //? if >=1.21.11 {
    /*@Inject(method = "setup", at = @At("TAIL"))
    private void gobbyclient$onCameraUpdate(Level area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        gobbyclient$applyFreeCam();
    }*/
    //?}

    @Unique
    private void gobbyclient$applyFreeCam() {
        if (!FreeCam.INSTANCE.getEnabled()) return;
        FreeCam.INSTANCE.updateMovement();
        setPosition(FreeCam.INSTANCE.getCamX(), FreeCam.INSTANCE.getCamY(), FreeCam.INSTANCE.getCamZ());
        setRotation(FreeCam.INSTANCE.getCamYaw(), FreeCam.INSTANCE.getCamPitch());
    }

    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$isThirdPerson(CallbackInfoReturnable<Boolean> cir) {
        if (FreeCam.INSTANCE.getEnabled()) {
            cir.setReturnValue(true);
        }
    }
}
