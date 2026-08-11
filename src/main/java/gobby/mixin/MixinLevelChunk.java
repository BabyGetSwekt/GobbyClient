package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.ChunkDataAppliedEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Consumer;

@Mixin(LevelChunk.class)
public class MixinLevelChunk {

    @Inject(method = "replaceWithPacketData", at = @At("TAIL"))
    private void gobbyclient$onChunkDataApplied(FriendlyByteBuf chunkData, Map<?, ?> blockEntities, Consumer<?> consumer, CallbackInfo ci) {
        Gobbyclient.EVENT_MANAGER.publish(new ChunkDataAppliedEvent((LevelChunk) (Object) this));
    }
}
