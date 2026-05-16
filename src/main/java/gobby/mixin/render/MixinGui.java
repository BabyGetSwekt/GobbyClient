package gobby.mixin.render;

import gobby.Gobbyclient;
import gobby.events.render.Render2DEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGui {

    /**
     * Used for rendering 2D elements on the player screen.
     */
    @Inject(method = "renderTabList(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void gobbyclient$onRenderPlayerList(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Render2DEvent event = new Render2DEvent(context, tickCounter);
        Gobbyclient.EVENT_MANAGER.publish(event);
    }
}
