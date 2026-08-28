package gobby.mixin;

import gobby.events.core.ChatSuppressor;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatListener.class)
public class MixinChatListener {

    @Inject(method = "handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onSystemMessage(Component message, boolean toChatHud, CallbackInfo ci) {
        if (ChatSuppressor.INSTANCE.consume(message)) {
            ci.cancel();
        }
    }
}
