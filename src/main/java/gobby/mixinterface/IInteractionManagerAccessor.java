package gobby.mixinterface;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;

public interface IInteractionManagerAccessor {

    void gobbyclient$syncSelectedSlot();

    void gobbyclient$sendSequencedPacket(ClientLevel world, GobbyclientSequencedPacketCreator creator);

    @FunctionalInterface
    interface GobbyclientSequencedPacketCreator {
        Packet<ServerGamePacketListener> predict(int sequence);
    }
}
