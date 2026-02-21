package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.*;
import gobby.events.gui.GuiOpenEvent;
import gobby.mixinterface.IMinecraftClient;
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
public abstract class MixinMinecraftClient implements IMinecraftClient {


    @Shadow public ClientWorld world;

    @Shadow protected abstract void doItemUse();

    @Unique
    private long gobbyclien$lastChecked = 0;

    @Unique
    private boolean rightClick;

    @Unique
    private boolean doItemUseCalled;


    /**
     * Changes the window title
     * @param cir window title
     */
    @Inject(method = "getWindowTitle()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onGetWindowTitle(CallbackInfoReturnable<String> cir) {
       cir.setReturnValue("Gobbyclient - " + Gobbyclient.MOD_VERSION + " - " + Gobbyclient.BETA_MODE);
       cir.cancel();
    }

    /**
     * Injects before the tick happens (at HEAD), preTickEvent
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void gobbyclient$onPreTick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.player != null || client.world != null) {
            Gobbyclient.EVENT_MANAGER.publish(ClientTickEvent.Pre.INSTANCE);
        }

        doItemUseCalled = false;

        if (rightClick && !doItemUseCalled) doItemUse();
        rightClick = false;
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

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V", at = @At("HEAD"))
    private void gobbyclient$onDisconnect(Screen screen, boolean transferring, CallbackInfo info) {
        if (world != null) {
            Gobbyclient.EVENT_MANAGER.publish(new DisconnectEvent());
        }
    }

    @Inject(method = "setWorld", at = @At("HEAD"))
    private void gobbyclient$onWorldLoad(ClientWorld world, CallbackInfo info) {
        if (world != null) {
            long now = System.currentTimeMillis();

            // TODO: Code to make it fire the onscoreboard event, so it gets the scoreboard when u join a world
            // Make sure it only fires once every 300 ms, for some reason it fires multiple times
            if (now - gobbyclien$lastChecked >= 300) {
                System.out.println("World loaded");
                Gobbyclient.EVENT_MANAGER.publish(new WorldLoadEvent());
                gobbyclien$lastChecked = now;
            }
        }
    }

    // Doesn't work yet, I don't know why
    @Inject(method = "setWorld", at = @At("TAIL"))
    private void gobbyclient$onWorldUnload(ClientWorld world, CallbackInfo info) {
        if (world == null) {
            System.out.println("World unloaded");
            Gobbyclient.EVENT_MANAGER.publish(new WorldUnloadEvent());
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
        doItemUseCalled = true;
        RightClickEvent event = new RightClickEvent();
        if (Gobbyclient.EVENT_MANAGER.publish(event).isCanceled()) ci.cancel();
    }

    @Override
    public void gobbyclient$rightClick() {
        rightClick = true;
    }
}
