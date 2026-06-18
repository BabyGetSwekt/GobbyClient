package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.*;
import gobby.features.skyblock.FreeCam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(value = Minecraft.class, priority = 1001)
public abstract class MixinMinecraft {


    @Shadow public ClientLevel level;

    @Unique
    private long gobbyclien$lastChecked = 0;

    /**
     * Injects before the tick happens (at HEAD), preTickEvent
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void gobbyclient$onPreTick(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.player != null || client.level != null) {
            Gobbyclient.EVENT_MANAGER.publish(ClientTickEvent.Pre.INSTANCE);
        }
    }

    /**
     * Injects after the tick happens (at TAIL), postTickEvent
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void gobbyclient$onPostTick(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.player != null || client.level != null) {
            Gobbyclient.EVENT_MANAGER.publish(ClientTickEvent.Post.INSTANCE);
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void gobbyclient$onDisconnect(Screen screen, boolean transferring, boolean savingWorld, CallbackInfo info) {
        if (level != null) {
            Gobbyclient.EVENT_MANAGER.publish(new DisconnectEvent());
        }
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void gobbyclient$onWorldLoad(ClientLevel world, CallbackInfo info) {
        gobbyclient$handleWorldLoad(world);
    }

    @Unique
    private void gobbyclient$handleWorldLoad(ClientLevel world) {
        if (world != null) {
            long now = System.currentTimeMillis();
            if (now - gobbyclien$lastChecked >= 300) {
                System.out.println("World loaded");
                Gobbyclient.EVENT_MANAGER.publish(new WorldLoadEvent());
                gobbyclien$lastChecked = now;
            }
        }
    }

    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void gobbyclient$freeCamCrosshair(float tickDelta, CallbackInfo ci) {
        if (!FreeCam.INSTANCE.getEnabled()) return;
        Minecraft client = (Minecraft) (Object) this;
        if (client.level == null || client.player == null) return;

        double reach = client.player.blockInteractionRange();

        Vec3 start = new Vec3(
                FreeCam.INSTANCE.getCamX(),
                FreeCam.INSTANCE.getCamY(),
                FreeCam.INSTANCE.getCamZ()
        );

        float yaw = FreeCam.INSTANCE.getCamYaw();
        float pitch = FreeCam.INSTANCE.getCamPitch();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double lookY = -Math.sin(pitchRad);
        double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);

        Vec3 end = start.add(lookX * reach, lookY * reach, lookZ * reach);

        BlockHitResult blockHit = client.level.clip(new ClipContext(
                start, end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                client.player
        ));

        client.hitResult = blockHit;
    }

    @Inject(method = "startAttack()Z", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        LeftClickEvent event = new LeftClickEvent();
        if (Gobbyclient.EVENT_MANAGER.publish(event).isCanceled()) cir.setReturnValue(false);
    }

    @Inject(method = "startUseItem()V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onDoItemUse(CallbackInfo ci) {
        RightClickEvent event = new RightClickEvent();
        if (Gobbyclient.EVENT_MANAGER.publish(event).isCanceled()) ci.cancel();
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onHandleBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (FreeCam.INSTANCE.getEnabled()) ci.cancel();
    }
}
