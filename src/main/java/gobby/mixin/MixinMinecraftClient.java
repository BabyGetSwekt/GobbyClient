package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.*;
import gobby.events.gui.GuiOpenEvent;
import gobby.features.skyblock.FreeCam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(value = MinecraftClient.class, priority = 1001)
public abstract class MixinMinecraftClient {


    @Shadow public ClientWorld world;

    @Unique
    private long gobbyclien$lastChecked = 0;

    /**
     * Injects before the tick happens (at HEAD), preTickEvent
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void gobbyclient$onPreTick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.player != null || client.world != null) {
            Gobbyclient.EVENT_MANAGER.publish(ClientTickEvent.Pre.INSTANCE);
        }
    }

    /**
     * Injects after the tick happens (at TAIL), postTickEvent
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void gobbyclient$onPostTick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.player != null || client.world != null) {
            Gobbyclient.EVENT_MANAGER.publish(ClientTickEvent.Post.INSTANCE);
        }
    }

    //? if <=1.21.10 {
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V", at = @At("HEAD"))
    private void gobbyclient$onDisconnect(Screen screen, boolean transferring, CallbackInfo info) {
        if (world != null) {
            Gobbyclient.EVENT_MANAGER.publish(new DisconnectEvent());
        }
    }
    //?}
    //? if >=1.21.11 {
    /*@Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;ZZ)V", at = @At("HEAD"))
    private void gobbyclient$onDisconnect(Screen screen, boolean transferring, boolean savingWorld, CallbackInfo info) {
        if (world != null) {
            Gobbyclient.EVENT_MANAGER.publish(new DisconnectEvent());
        }
    }*/
    //?}

    //? if <=1.21.10 {
    @Inject(method = "setWorld", at = @At("HEAD"))
    private void gobbyclient$onWorldLoad(ClientWorld world, CallbackInfo info) {
        gobbyclient$handleWorldLoad(world);
    }
    //?}
    //? if >=1.21.11 {
    /*@Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Z)V", at = @At("HEAD"))
    private void gobbyclient$onWorldLoad(ClientWorld world, boolean refresh, CallbackInfo info) {
        gobbyclient$handleWorldLoad(world);
    }*/
    //?}

    @Unique
    private void gobbyclient$handleWorldLoad(ClientWorld world) {
        if (world != null) {
            long now = System.currentTimeMillis();
            if (now - gobbyclien$lastChecked >= 300) {
                System.out.println("World loaded");
                Gobbyclient.EVENT_MANAGER.publish(new WorldLoadEvent());
                gobbyclien$lastChecked = now;
            }
        }
    }

    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("TAIL"))
    private void gobbyclient$onSetScreen(Screen screen, CallbackInfo info) {
        if (screen == null) return;
        Gobbyclient.EVENT_MANAGER.publish(new GuiOpenEvent(screen));
    }

    @Inject(method = "doAttack()Z", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        LeftClickEvent event = new LeftClickEvent();
        if (Gobbyclient.EVENT_MANAGER.publish(event).isCanceled()) cir.setReturnValue(false);
    }

    @Inject(method = "doItemUse()V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onDoItemUse(CallbackInfo ci) {
        RightClickEvent event = new RightClickEvent();
        if (Gobbyclient.EVENT_MANAGER.publish(event).isCanceled()) ci.cancel();
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onHandleBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (FreeCam.INSTANCE.getEnabled()) ci.cancel();
    }
}
