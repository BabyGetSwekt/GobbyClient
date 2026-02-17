package gobby.mixin.network;

import gobby.Gobbyclient;
import gobby.events.SpawnParticleEvent;
import gobby.events.network.ClientConnectedToServerEvent;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {


    /**
     * This event fires when the client joins a server.
     * Used to determine what server the client is connected to.
     */
    @Inject(method = "onGameJoin(Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;)V", at = @At("HEAD"))
    private void onGamJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        ClientConnectedToServerEvent event = new ClientConnectedToServerEvent();
        Gobbyclient.EVENT_MANAGER.publish(event);
    }

    @Inject(method = "onParticle(Lnet/minecraft/network/packet/s2c/play/ParticleS2CPacket;)V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onParticle(ParticleS2CPacket packet, CallbackInfo ci) {
        SpawnParticleEvent event = new SpawnParticleEvent(packet, packet.getParameters().getType(), new Vec3d(packet.getX(), packet.getY(), packet.getZ()));
        Gobbyclient.EVENT_MANAGER.publish(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}
