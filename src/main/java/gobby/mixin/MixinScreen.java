package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.KeyPressGuiEvent;
import gobby.events.gui.ScreenRenderEvent;
import gobby.features.dungeons.LeapOverlay;
import gobby.features.floor7.terminals.TerminalOverlay;
import gobby.gui.brush.BlockSelector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Screen.class)
public class MixinScreen {

	@Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
	private void gobbyclient$onGuiKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
		KeyPressGuiEvent event = Gobbyclient.EVENT_MANAGER.publish(new KeyPressGuiEvent(input.key()));
		if (event.isCanceled()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
	private void gobbyclient$captureDrawContext(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		BlockSelector.Companion.setCurrentDrawContext(context);
		if (LeapOverlay.INSTANCE.isOverlayActive() || TerminalOverlay.INSTANCE.isOverlayActive()) {
			Gobbyclient.EVENT_MANAGER.publish(new ScreenRenderEvent((Screen)(Object)this, context, mouseX, mouseY, delta));
			BlockSelector.Companion.setCurrentDrawContext(null);
			ci.cancel();
		}
	}

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
	private void gobbyclient$drawItemsAndReleaseContext(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		BlockSelector.drawBlockItemsIfActive(context);
		BlockSelector.Companion.setCurrentDrawContext(null);
	}

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
	private void gobbyclient$onScreenRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		Gobbyclient.EVENT_MANAGER.publish(new ScreenRenderEvent((Screen)(Object)this, context, mouseX, mouseY, delta));
	}
}
