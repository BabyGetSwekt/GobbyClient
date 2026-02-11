package gobby.mixin.render;

import gobby.Gobbyclient;
import gobby.events.render.GammaEvent;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;



@Mixin(LightmapTextureManager.class)
public class MixinLightmapTextureManager {

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
    private float gobbyclient$updateFloatGamma(Double instance) {
        GammaEvent event = new GammaEvent(instance.floatValue());
        Gobbyclient.EVENT_MANAGER.publish(event);
        return event.getGamma();
    }
}