package gobby.mixin;

import gobby.features.skyblock.FreeCam;
import gobby.mixin.accessor.WalkAnimationStateAccessor;
import gobby.pathfinder.movement.InputManager;
import gobby.utils.managers.PacketOrderManager;
import gobby.utils.rotation.ServerRotationLeaseManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @Shadow private float yRotLast;
    @Shadow private float xRotLast;
    @Unique private ServerRotationLeaseManager.MovementRotation gobbyclient$serverRotation;

    @Inject(method = "tick", at = @At("RETURN"))
    private void gobbyclient$afterTick(CallbackInfo ci) {
        if (!FreeCam.INSTANCE.getEnabled()) return;
        LocalPlayer self = (LocalPlayer) (Object) this;

        Vec3 vel = self.getDeltaMovement();
        double horizontalSpeed = vel.x * vel.x + vel.z * vel.z;
        if (horizontalSpeed < 0.0001) {
            self.walkAnimation.setSpeed(0f);
            ((WalkAnimationStateAccessor) self.walkAnimation).setLastSpeed(0f);
        }
    }

    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$suppressSprintStart(CallbackInfoReturnable<Boolean> cir) {
        if (InputManager.suppressSprint) cir.setReturnValue(false);
    }

    @Inject(method = "shouldStopRunSprinting", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$forceStopSprint(CallbackInfoReturnable<Boolean> cir) {
        if (InputManager.suppressSprint) cir.setReturnValue(true);
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void gobbyclient$beforeSendMovement(CallbackInfo ci) {
        gobbyclient$serverRotation = ServerRotationLeaseManager.INSTANCE.beginMovementTick();
        PacketOrderManager.INSTANCE.execute(PacketOrderManager.Phase.ITEM_USE);
    }

    @ModifyVariable(method = "sendPosition", at = @At("STORE"), name = "deltaYRot")
    private double gobbyclient$serverRotationYawDelta(double deltaYRot) {
        return gobbyclient$serverRotation == null ? deltaYRot : gobbyclient$serverRotation.getYaw() - yRotLast;
    }

    @ModifyVariable(method = "sendPosition", at = @At("STORE"), name = "deltaXRot")
    private double gobbyclient$serverRotationPitchDelta(double deltaXRot) {
        return gobbyclient$serverRotation == null ? deltaXRot : gobbyclient$serverRotation.getPitch() - xRotLast;
    }

    @ModifyArgs(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket$PosRot;<init>(Lnet/minecraft/world/phys/Vec3;FFZZ)V"))
    private void gobbyclient$applyServerRotationToPosRot(Args args) {
        if (gobbyclient$serverRotation == null) return;
        args.set(1, gobbyclient$serverRotation.getYaw());
        args.set(2, gobbyclient$serverRotation.getPitch());
    }

    @ModifyArgs(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket$Rot;<init>(FFZZ)V"))
    private void gobbyclient$applyServerRotationToRot(Args args) {
        if (gobbyclient$serverRotation == null) return;
        args.set(0, gobbyclient$serverRotation.getYaw());
        args.set(1, gobbyclient$serverRotation.getPitch());
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void gobbyclient$afterSendMovement(CallbackInfo ci) {
        PacketOrderManager.INSTANCE.execute(PacketOrderManager.Phase.AFTER_MOVEMENT);
        ServerRotationLeaseManager.MovementRotation captured = gobbyclient$serverRotation;
        if (captured != null) {
            yRotLast = captured.getYaw();
            xRotLast = captured.getPitch();
            LocalPlayer player = (LocalPlayer) (Object) this;
            player.setYRot(ServerRotationLeaseManager.INSTANCE.nearestEquivalentYaw(player.getYRot(), captured.getYaw()));
        }
        ServerRotationLeaseManager.INSTANCE.finishMovementTick(captured);
        gobbyclient$serverRotation = null;
    }
}
