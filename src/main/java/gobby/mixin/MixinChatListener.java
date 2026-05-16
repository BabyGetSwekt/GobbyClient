package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.ChatReceivedEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatListener.class, priority = 600)
public class MixinChatListener {

    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void gobbyclient$monitorGameMessage(Component message, boolean overlay, CallbackInfo ci) {
        if (overlay) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        Gobbyclient.EVENT_MANAGER.publish(new ChatReceivedEvent(message.getString()));
    }
}
