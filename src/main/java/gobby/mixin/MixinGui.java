package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.gui.GuiOpenEvent;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGui {

    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("TAIL"))
    private void gobbyclient$onSetScreen(Screen screen, CallbackInfo info) {
        if (screen == null) return;
        Gobbyclient.EVENT_MANAGER.publish(new GuiOpenEvent(screen));
    }
}
