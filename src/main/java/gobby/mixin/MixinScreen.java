package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.KeyPressGuiEvent;
import gobby.gui.brush.BlockSelector;
import gobby.gui.components.KeybindPropertyComponent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

	@Inject(method = "renderWithTooltip", at = @At("HEAD"))
	private void gobbyclient$captureDrawContext(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		BlockSelector.Companion.setCurrentDrawContext(context);
	}

	@Inject(method = "renderWithTooltip", at = @At("RETURN"))
	private void gobbyclient$drawItemsAndReleaseContext(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		BlockSelector.drawBlockItemsIfActive(context);
		BlockSelector.Companion.setCurrentDrawContext(null);
	}
}
