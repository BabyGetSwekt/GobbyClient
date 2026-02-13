package gobby.mixin;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Mixin class to stay under the radar hehe
 */
@Mixin(value = FabricLoaderImpl.class, remap = false)
public class MixinFabricLoaderImpl {

    @Unique
    private static final List<String> HIDDEN_MODS = List.of("gobbyclient", "devoniandoogan");

    @Inject(method = "getAllMods", at = @At("RETURN"), cancellable = true, require = 0)
    private void gobby$hideFromAllMods(CallbackInfoReturnable<Collection<ModContainer>> cir) {
        cir.setReturnValue(
            cir.getReturnValue().stream()
                .filter(mod -> !HIDDEN_MODS.contains(mod.getMetadata().getId()))
                .toList()
        );
    }

    @Inject(method = "getModContainer", at = @At("RETURN"), cancellable = true, require = 0)
    private void gobby$hideFromGetContainer(String id, CallbackInfoReturnable<Optional<ModContainer>> cir) {
        if (HIDDEN_MODS.contains(id)) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "isModLoaded", at = @At("RETURN"), cancellable = true, require = 0)
    private void gobby$hideFromIsLoaded(String id, CallbackInfoReturnable<Boolean> cir) {
        if (HIDDEN_MODS.contains(id)) {
            cir.setReturnValue(false);
        }
    }
}
