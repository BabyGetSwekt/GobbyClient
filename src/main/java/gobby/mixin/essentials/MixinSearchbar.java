package gobby.mixin.essentials;

import gg.essential.vigilance.gui.Searchbar;
import gobby.Gobbyclient;
import gobby.events.core.SubscribeEvent;
import gobby.gui.components.KeybindPropertyComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Searchbar.class, remap = false)
public class MixinSearchbar {


    @Inject(method = "activateSearch()V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$activateSearch(CallbackInfo ci) {
        System.out.println("Triggered this class");

//        if (KeybindPropertyComponent.isListening()) {
//            Gobbyclient.getLogger().info("Cancelling class");
//            // Never enters this
//            ci.cancel();
//        }
    }

    @Inject(method = "setText(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$setText(String text, CallbackInfo ci) {
        System.out.println("Triggered setText class");

//        if (KeybindPropertyComponent.isListening()) {
//            Gobbyclient.getLogger().info("Cancelling setText class");
//            // Never enters this
//            ci.cancel();
//        }
    }
}

