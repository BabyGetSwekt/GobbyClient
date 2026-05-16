package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.MouseButtonEvent;
import gobby.features.skyblock.FreeCam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    @Shadow @Final private Minecraft minecraft;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "onButton", at = @At("HEAD"))
    private void gobbyclient$onMouseButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        Gobbyclient.EVENT_MANAGER.publish(new MouseButtonEvent(input.button(), action));
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void gobbyclient$onUpdateMouse(double delta, CallbackInfo ci) {
        if (!FreeCam.INSTANCE.getEnabled()) return;
        double sensitivity = this.minecraft.options.sensitivity().get() * 0.6 + 0.2;
        double factor = sensitivity * sensitivity * sensitivity * 8.0;
        float dx = (float) (this.accumulatedDX * factor);
        float dy = (float) (this.accumulatedDY * factor);
        FreeCam.INSTANCE.updateAngles(dx, dy);
        this.accumulatedDX = 0;
        this.accumulatedDY = 0;
    }
}
