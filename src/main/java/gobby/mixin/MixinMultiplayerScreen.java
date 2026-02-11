package gobby.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public abstract class MixinMultiplayerScreen extends Screen {

    protected MixinMultiplayerScreen() {
        super(null);
    }
//
//    @Inject(method = "init", at = @At("TAIL"))
//    public void onInit(CallbackInfo ci) {
//        this.addDrawableChild(ButtonWidget.builder(
//                Text.of("Gobby Ratter :("),
//                button -> {
//                    MinecraftClient.getInstance().setScreen(new SessionLoginScreen((Screen) (Object) this));
//                }
//        ).position(5, 6).size(98, 20).build());
// /   }
}
