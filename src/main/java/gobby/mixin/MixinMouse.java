package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.MouseButtonEvent;
import gobby.features.skyblock.FreeCam;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MixinMouse {

    @Shadow @Final private MinecraftClient client;
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void gobbyclient$onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        Gobbyclient.EVENT_MANAGER.publish(new MouseButtonEvent(input.button(), action));
    }

    @Inject(method = "updateMouse", at = @At("HEAD"))
    private void gobbyclient$onUpdateMouse(double delta, CallbackInfo ci) {
        if (!FreeCam.INSTANCE.getEnabled()) return;
        double sensitivity = this.client.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        double factor = sensitivity * sensitivity * sensitivity * 8.0;
        float dx = (float) (this.cursorDeltaX * factor);
        float dy = (float) (this.cursorDeltaY * factor);
        FreeCam.INSTANCE.updateAngles(dx, dy);
        this.cursorDeltaX = 0;
        this.cursorDeltaY = 0;
    }
}
