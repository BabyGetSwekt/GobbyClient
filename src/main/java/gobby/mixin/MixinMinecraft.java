package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.*;
import gobby.features.skyblock.FreeCam;
import gobby.utils.rotation.ServerRotationLeaseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ClipContext;
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

    @Unique
    private static final long WORLD_LOAD_DEBOUNCE_MS = 300L;

    @Shadow public ClientLevel level;

    @Unique
    private long gobbyclient$lastWorldLoad = 0;

    /**
     * Injects before the tick happens (at HEAD), preTickEvent
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void gobbyclient$onPreTick(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        ServerRotationLeaseManager.INSTANCE.beginClientTick();
        ServerRotationLeaseManager.INSTANCE.observeClientState(
                client.level,
                client.player != null && client.getConnection() != null
        );
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
    private void gobbyclient$onDisconnect(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo info) {
        if (level != null) {
            Gobbyclient.EVENT_MANAGER.publish(new DisconnectEvent());
        }
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void gobbyclient$onWorldLoad(ClientLevel level, CallbackInfo info) {
        gobbyclient$handleWorldLoad(level);
    }

    @Unique
    private void gobbyclient$handleWorldLoad(ClientLevel loaded) {
        if (loaded == null) return;
        long now = System.currentTimeMillis();
        if (now - gobbyclient$lastWorldLoad < WORLD_LOAD_DEBOUNCE_MS) return;
        System.out.println("World loaded");
        Gobbyclient.EVENT_MANAGER.publish(new WorldLoadEvent());
        gobbyclient$lastWorldLoad = now;
    }

    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void gobbyclient$freeCamCrosshair(float partialTicks, CallbackInfo ci) {
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

        client.hitResult = client.level.clip(new ClipContext(
                start, end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                client.player
        ));
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
    private void gobbyclient$onHandleBlockBreaking(boolean down, CallbackInfo ci) {
        if (FreeCam.INSTANCE.getEnabled()) ci.cancel();
    }
}
