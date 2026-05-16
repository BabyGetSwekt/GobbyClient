package gobby.mixin.network;

import gobby.Gobbyclient;
import gobby.events.PacketSentEvent;
import gobby.events.PacketReceivedEvent;
import gobby.mixinterface.IClientConnectionAccessor;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinConnection implements IClientConnectionAccessor {

    @Unique public int interactSequence;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onSendPacket(Packet<?> packet, @Nullable ChannelFutureListener listener, CallbackInfo ci) {
        if (Gobbyclient.EVENT_MANAGER.publish(new PacketSentEvent(packet)).isCanceled()) {
            ci.cancel();
            return;
        }

        if (packet instanceof ServerboundUseItemPacket) interactSequence++;
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V", shift = At.Shift.BEFORE), cancellable = true, require = 1)
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
