package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.KeyPressGuiEvent;
import gobby.gui.components.KeybindPropertyComponent;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Screen.class)
public class MixinScreen {

	@Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyInput;)Z", at = @At("HEAD"), cancellable = true)
	private void gobbyclient$onGuiKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
		if (KeybindPropertyComponent.isListening()) {
			KeybindPropertyComponent.handleKeyPress(input.key());
			cir.setReturnValue(true);
			return;
		}

		KeyPressGuiEvent event = Gobbyclient.EVENT_MANAGER.publish(new KeyPressGuiEvent(input.key()));
		if (event.isCanceled()) {
			cir.setReturnValue(true);
		}
	}
}
