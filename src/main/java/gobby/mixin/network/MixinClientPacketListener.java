package gobby.mixin.network;

import gobby.Gobbyclient;
import gobby.events.ScreenReceivedEvent;
import gobby.events.network.ClientConnectedToServerEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {


    /**
     * This event fires when the client joins a server.
     * Used to determine what server the client is connected to.
     */
    @Inject(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V", at = @At("HEAD"))
    private void onGamJoin(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientConnectedToServerEvent event = new ClientConnectedToServerEvent();
        Gobbyclient.EVENT_MANAGER.publish(event);
    }

    @Inject(method = "handleOpenScreen(Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;)V", at = @At("TAIL"))
    private void gobbyclient$onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        Gobbyclient.EVENT_MANAGER.publish(new ScreenReceivedEvent(packet.getTitle().getString(), packet.getContainerId()));
    }

}
