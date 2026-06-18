package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.gui.GuiOpenEvent;
//? if >26.1.2
import net.minecraft.client.gui.Gui;
//? if <=26.1.2
/*import net.minecraft.client.Minecraft;*/
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >26.1.2
@Mixin(Gui.class)
//? if <=26.1.2
/*@Mixin(Minecraft.class)*/
public class MixinGui {

    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("TAIL"))
    private void gobbyclient$onSetScreen(Screen screen, CallbackInfo info) {
        if (screen == null) return;
        Gobbyclient.EVENT_MANAGER.publish(new GuiOpenEvent(screen));
    }
}
