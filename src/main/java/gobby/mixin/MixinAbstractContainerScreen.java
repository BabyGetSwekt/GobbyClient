package gobby.mixin;

import gobby.features.developer.DrawSlotNumbers;
import gobby.features.dungeons.LeapOverlay;
import gobby.features.floor7.terminals.TerminalOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class MixinAbstractContainerScreen {

	//? if <=1.21.10 {
	@Inject(method = "renderSlots", at = @At("RETURN"))
	private void gobbyclient$onDrawSlots(GuiGraphics context, CallbackInfo ci) {
		DrawSlotNumbers.INSTANCE.onDrawSlots((AbstractContainerScreen<?>)(Object)this, context);
	}
	//?}
	//? if >=1.21.11 {
	/*@Inject(method = "renderSlots", at = @At("RETURN"))
	private void gobbyclient$onDrawSlots(GuiGraphics context, int mouseX, int mouseY, CallbackInfo ci) {
		DrawSlotNumbers.INSTANCE.onDrawSlots((AbstractContainerScreen<?>)(Object)this, context);
	}*/
	//?}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void gobbyclient$cancelMouseClicked(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		if (LeapOverlay.INSTANCE.isOverlayActive()) {
			if (click.button() == 0) LeapOverlay.INSTANCE.handleClick(click.x(), click.y());
			cir.setReturnValue(true);
			return;
		}
		if (TerminalOverlay.INSTANCE.shouldBlockClicks()) cir.setReturnValue(true);
	}

	@Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
	private void gobbyclient$cancelMouseReleased(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
		if (TerminalOverlay.INSTANCE.shouldBlockClicks() || LeapOverlay.INSTANCE.isOverlayActive()) cir.setReturnValue(true);
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void gobbyclient$cancelMouseDragged(MouseButtonEvent click, double offsetX, double offsetY, CallbackInfoReturnable<Boolean> cir) {
		if (TerminalOverlay.INSTANCE.shouldBlockClicks() || LeapOverlay.INSTANCE.isOverlayActive()) cir.setReturnValue(true);
	}
}
