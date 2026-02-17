package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.CharTypedEvent;
import gobby.events.KeyPressGuiEvent;
import gobby.gui.components.KeybindPropertyComponent;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class MixinKeyboard {

    @Shadow private MinecraftClient client;

    @Inject(method = "onKey(JILnet/minecraft/client/input/KeyInput;)V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onKeyPressed(long window, int action, KeyInput input, CallbackInfo ci) {
        int key = input.key();

        if (KeybindPropertyComponent.isListening() && action == GLFW.GLFW_PRESS) {
            KeybindPropertyComponent.handleKeyPress(key);
            ci.cancel();
            return;
        }

        if ((action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) && key != GLFW.GLFW_KEY_UNKNOWN && client.world != null) {
            KeyPressGuiEvent event = Gobbyclient.EVENT_MANAGER.publish(new KeyPressGuiEvent(key));

            if (event.isCanceled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "onChar(JLnet/minecraft/client/input/CharInput;)V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onCharTyped(long window, CharInput input, CallbackInfo ci) {
        if (KeybindPropertyComponent.isListening() || KeybindPropertyComponent.shouldSuppressChar()) {
            ci.cancel();
            return;
        }

        CharTypedEvent event = Gobbyclient.EVENT_MANAGER.publish(new CharTypedEvent(input.codepoint()));

        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}
