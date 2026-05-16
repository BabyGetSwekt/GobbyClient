package gobby.mixin.network;

import gobby.mixinterface.IInteractionManagerAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode implements IInteractionManagerAccessor {

    @Invoker("startPrediction")
    public abstract void gobbyclient$invokeSendSequencedPacket(ClientLevel world, PredictiveAction packetCreator);

    @Invoker("ensureHasSentCarriedItem")
    public abstract void gobbyclient$invokeSyncSelectedSlot();

    @Override
    public void gobbyclient$syncSelectedSlot() {
        gobbyclient$invokeSyncSelectedSlot();
    }

    @Override
    public void gobbyclient$sendSequencedPacket(ClientLevel world, GobbyclientSequencedPacketCreator creator) {
        gobbyclient$invokeSendSequencedPacket(world, creator::predict);
    }
}
