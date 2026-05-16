package gobby.mixin;


import gobby.Gobbyclient;
import gobby.events.BlockStateChangeEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class MixinClientLevel {

    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onBlockUpdate(BlockPos pos, BlockState newState, int flags, CallbackInfo ci) {
        ClientLevel world = (ClientLevel) (Object) this;
        BlockState oldState = world.getBlockState(pos);
        BlockStateChangeEvent event = Gobbyclient.EVENT_MANAGER.publish(new BlockStateChangeEvent(pos.immutable(), oldState, newState));
        if (event.isCanceled()) ci.cancel();
    }
}
