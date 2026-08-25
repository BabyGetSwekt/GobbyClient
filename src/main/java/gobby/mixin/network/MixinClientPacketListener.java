package gobby.mixin.network;

import gobby.Gobbyclient;
import gobby.events.ScreenReceivedEvent;
import gobby.events.network.ClientConnectedToServerEvent;
import gobby.pathfinder.etherwarp.EtherwarpPathExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
    @Unique private LocalPlayer gobbyclient$teleportPlayer;
    @Unique private net.minecraft.world.phys.Vec3 gobbyclient$preTeleportPosition;

    @Inject(
        method = "handleMovePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER
        )
    )
    private void gobbyclient$beforeAuthoritativeTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        gobbyclient$teleportPlayer = null;
        gobbyclient$preTeleportPosition = null;
        LocalPlayer player = Minecraft.getInstance().player;
        gobbyclient$teleportPlayer = EtherwarpPathExecutor.INSTANCE.shouldCaptureTeleportHistory() ? player : null;
        gobbyclient$preTeleportPosition = gobbyclient$teleportPlayer == null ? null : gobbyclient$teleportPlayer.position();
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void gobbyclient$afterAuthoritativeTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == gobbyclient$teleportPlayer && gobbyclient$preTeleportPosition != null) {
            EtherwarpPathExecutor.INSTANCE.onAuthoritativeTeleport(gobbyclient$preTeleportPosition, gobbyclient$teleportPlayer.position(), System.nanoTime());
        }
        gobbyclient$teleportPlayer = null;
        gobbyclient$preTeleportPosition = null;
    }


    @Inject(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V", at = @At("HEAD"))
    private void gobbyclient$onGamJoin(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientConnectedToServerEvent event = new ClientConnectedToServerEvent();
        Gobbyclient.EVENT_MANAGER.publish(event);
    }

    @Inject(method = "handleOpenScreen(Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;)V", at = @At("TAIL"))
    private void gobbyclient$onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        Gobbyclient.EVENT_MANAGER.publish(new ScreenReceivedEvent(packet.getTitle().getString(), packet.getContainerId()));
    }

}
