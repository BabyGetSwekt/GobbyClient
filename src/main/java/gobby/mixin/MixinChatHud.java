package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.ChatReceivedEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class MixinChatHud {

    /**
     * When cancelling this event it will not be visible to the player (you).
     * This makes it so other mods can still pick up on it.
     */
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onChatMessage(Text message, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (Gobbyclient.EVENT_MANAGER.publish(new ChatReceivedEvent(message.getString())).isCanceled()) ci.cancel();
    }
}
