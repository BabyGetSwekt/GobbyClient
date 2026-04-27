package gobby.mixinterface;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.Packet;

public interface IInteractionManagerAccessor {

    void gobbyclient$syncSelectedSlot();

    void gobbyclient$sendSequencedPacket(ClientWorld world, GobbyclientSequencedPacketCreator creator);

    @FunctionalInterface
    interface GobbyclientSequencedPacketCreator {
        Packet<ServerPlayPacketListener> predict(int sequence);
    }
}
