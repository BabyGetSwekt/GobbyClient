package gobby.mixin;

import gobby.features.floor7.terminals.TerminalOverlay;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public class MixinHandledScreen {

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void gobbyclient$cancelMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		if (TerminalOverlay.INSTANCE.isOverlayActive()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
	private void gobbyclient$cancelMouseReleased(Click click, CallbackInfoReturnable<Boolean> cir) {
		if (TerminalOverlay.INSTANCE.isOverlayActive()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void gobbyclient$cancelMouseDragged(Click click, double offsetX, double offsetY, CallbackInfoReturnable<Boolean> cir) {
		if (TerminalOverlay.INSTANCE.isOverlayActive()) {
			cir.setReturnValue(true);
		}
	}
}
