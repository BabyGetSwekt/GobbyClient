package gobby.mixin;

import static gobby.utils.ChatUtils.modMessage;

import gobby.Gobbyclient;
import gobby.events.BlockStateChangeEvent;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class MixinClientWorld {

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onBlockUpdate(BlockPos pos, BlockState newState, int flags, CallbackInfo ci) {
        ClientWorld world = (ClientWorld) (Object) this;
        BlockState oldState = world.getBlockState(pos);
        BlockStateChangeEvent event = Gobbyclient.EVENT_MANAGER.publish(new BlockStateChangeEvent(pos.toImmutable(), oldState, newState));
        if (event.isCanceled()) ci.cancel();
    }
}
