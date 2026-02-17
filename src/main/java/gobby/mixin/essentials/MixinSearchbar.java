package gobby.mixin.essentials;

import gg.essential.vigilance.gui.Searchbar;
import gobby.gui.components.KeybindPropertyComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Searchbar.class, remap = false)
public class MixinSearchbar {

	@Inject(method = "activateSearch()V", at = @At("HEAD"), cancellable = true)
	private void gobbyclient$activateSearch(CallbackInfo ci) {
		if (KeybindPropertyComponent.isListening()) {
			ci.cancel();
		}
	}

	@Inject(method = "setText(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
	private void gobbyclient$setText(String text, CallbackInfo ci) {
		if (KeybindPropertyComponent.isListening()) {
			ci.cancel();
		}
	}
}
