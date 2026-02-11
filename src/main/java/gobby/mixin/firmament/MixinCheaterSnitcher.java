package gobby.mixin.firmament;

import moe.nea.firmament.features.misc.ModAnnouncer;
import net.fabricmc.loader.api.FabricLoader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModAnnouncer.class, remap = false)
public class MixinCheaterSnitcher {
	@Inject(method = "onServerJoin", at = @At("HEAD"), cancellable = true, remap = false)
	private void onServerJoin(CallbackInfo ci) {
		if (FabricLoader.getInstance().isModLoaded("firmament")) { // Not necessary, idek why I put this here
			System.out.println("Detected Firmament, cancelling mod list packet.");
			ci.cancel();
			System.out.println("Cancelled mod list packet.");
		}
	}
}
