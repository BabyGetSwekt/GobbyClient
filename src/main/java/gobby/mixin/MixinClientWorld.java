package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.BlockStateChangeEvent;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class MixinClientWorld {

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void gobbyclient$onBlockUpdate(BlockPos pos, BlockState newState, int flags, CallbackInfo ci) {
        ClientWorld world = (ClientWorld) (Object) this;
        BlockState oldState = world.getBlockState(pos);
        Gobbyclient.EVENT_MANAGER.publish(new BlockStateChangeEvent(pos.toImmutable(), oldState, newState));
    }
}
