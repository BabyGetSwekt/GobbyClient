package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.MouseButtonEvent;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MixinMouse {

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void gobbyclient$onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        Gobbyclient.EVENT_MANAGER.publish(new MouseButtonEvent(input.button(), action));
    }
}
