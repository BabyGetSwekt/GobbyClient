package gobby.mixin.accessor;

import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundMoveEntityPacket.class)
public interface MoveEntityPacketAccessor {

    @Accessor("xa")
    short getDeltaX();

    @Accessor("ya")
    short getDeltaY();

    @Accessor("za")
    short getDeltaZ();
}
