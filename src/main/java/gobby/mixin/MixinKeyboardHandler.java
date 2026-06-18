package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.CharTypedEvent;
import gobby.events.KeyPressGuiEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onKeyPressed(long window, int action, KeyEvent input, CallbackInfo ci) {
        int key = input.key();

        if (action == GLFW.GLFW_PRESS && key != GLFW.GLFW_KEY_UNKNOWN && minecraft.level != null) {
            KeyPressGuiEvent event = Gobbyclient.EVENT_MANAGER.publish(new KeyPressGuiEvent(key));

            if (event.isCanceled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onCharTyped(long window, CharacterEvent input, CallbackInfo ci) {
        CharTypedEvent event = Gobbyclient.EVENT_MANAGER.publish(new CharTypedEvent(input.codepoint()));

        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}
