package gobby.mixin.network;

import gobby.Gobbyclient;
import gobby.events.PacketSentEvent;
import gobby.events.PacketReceivedEvent;
import gobby.mixinterface.IClientConnectionAccessor;
import gobby.pathfinder.etherwarp.EtherwarpServerTickGate;
import gobby.utils.rotation.ServerRotationLeaseManager;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinConnection implements IClientConnectionAccessor {

    @Unique public int interactSequence;

    @ModifyVariable(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private Packet<?> gobbyclient$rewriteServerRotatedInteraction(Packet<?> packet) {
        return ServerRotationLeaseManager.INSTANCE.rewriteOutgoingInteraction(packet);
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onSendPacket(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (Gobbyclient.EVENT_MANAGER.publish(new PacketSentEvent(packet)).isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("RETURN"))
    private void gobbyclient$observeAcceptedOutgoing(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        ServerRotationLeaseManager.INSTANCE.observeAcceptedOutgoing(packet);
        if (packet instanceof ServerboundUseItemPacket) interactSequence++;
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onReceivePacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        try {
            ServerRotationLeaseManager.INSTANCE.observeIncoming(packet);
            if (packet instanceof ClientboundPingPacket ping) EtherwarpServerTickGate.INSTANCE.onServerTickPing(ping.getId());
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
