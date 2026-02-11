package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.ChatReceivedEvent;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MessageHandler.class, priority = 600)
public class MixinMessageHandler {

    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void gobbyclient$monitorGameMessage(Text message, boolean overlay, CallbackInfo ci) {
        if (overlay) return;
        Gobbyclient.EVENT_MANAGER.publish(new ChatReceivedEvent(message));
    }
}
