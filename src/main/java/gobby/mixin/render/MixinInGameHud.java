package gobby.mixin.render;

import gobby.Gobbyclient;
import gobby.events.render.Render2DEvent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class MixinInGameHud {

    /**
     * Used for rendering 2D elements on the player screen.
     */
    @Inject(method = "renderPlayerList(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
    private void gobbyclient$onRenderPlayerList(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Render2DEvent event = new Render2DEvent(context, tickCounter);
        Gobbyclient.EVENT_MANAGER.publish(event);
    }
}
