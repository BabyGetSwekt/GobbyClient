package gobby.mixin.network;

import gobby.Gobbyclient;
import gobby.events.PacketSentEvent;
import gobby.events.PacketReceivedEvent;
import gobby.mixinterface.IClientConnectionAccessor;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class MixinClientConnection implements IClientConnectionAccessor {

    @Unique public int interactSequence;

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"))
    private void gobbyclient$onSendPacket(Packet<?> packet, @Nullable ChannelFutureListener listener, CallbackInfo ci) {
        Gobbyclient.EVENT_MANAGER.publish(new PacketSentEvent(packet));

        if (packet instanceof PlayerInteractItemC2SPacket) interactSequence++;
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V", shift = At.Shift.BEFORE), cancellable = true, require = 1)
    private void gobbyclient$onReceivePacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        try {
            if (Gobbyclient.EVENT_MANAGER.publish(new PacketReceivedEvent(packet)).isCanceled()) ci.cancel();
        } catch (Exception e) {
            System.out.println("[GobbyClient] PacketReceivedEvent error: " + e.getMessage());
        }
    }

    @Override
    public int getInteractSequence() {
        return interactSequence;
    }
}
