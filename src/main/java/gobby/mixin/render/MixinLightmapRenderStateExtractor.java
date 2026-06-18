package gobby.mixin.render;

import gobby.Gobbyclient;
import gobby.events.render.GammaEvent;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;



@Mixin(LightmapRenderStateExtractor.class)
public class MixinLightmapRenderStateExtractor {

    @Redirect(method = "extract", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0))
    private float gobbyclient$updateFloatGamma(Double instance) {
        GammaEvent event = new GammaEvent(instance.floatValue());
        Gobbyclient.EVENT_MANAGER.publish(event);
        return event.getGamma();
    }
}
