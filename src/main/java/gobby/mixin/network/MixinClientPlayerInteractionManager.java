package gobby.mixin.network;

import gobby.mixinterface.IInteractionManagerAccessor;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.SequencedPacketCreator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class MixinClientPlayerInteractionManager implements IInteractionManagerAccessor {

    @Invoker("sendSequencedPacket")
    public abstract void gobbyclient$invokeSendSequencedPacket(ClientWorld world, SequencedPacketCreator packetCreator);

    @Invoker("syncSelectedSlot")
    public abstract void gobbyclient$invokeSyncSelectedSlot();

    @Override
    public void gobbyclient$syncSelectedSlot() {
        gobbyclient$invokeSyncSelectedSlot();
    }

    @Override
    public void gobbyclient$sendSequencedPacket(ClientWorld world, GobbyclientSequencedPacketCreator creator) {
        gobbyclient$invokeSendSequencedPacket(world, (sequence) -> creator.predict(sequence));
    }
}
