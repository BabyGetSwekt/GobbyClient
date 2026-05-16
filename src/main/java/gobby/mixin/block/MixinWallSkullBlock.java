package gobby.mixin.block;

import gobby.features.dungeons.SecretHitbox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WallSkullBlock.class)
public class MixinWallSkullBlock {

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void onGetOutlineShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!SecretHitbox.INSTANCE.getEnabled()) return;
        if (!SecretHitbox.inCollisionCheck && SecretHitbox.INSTANCE.isSecretSkull(world, pos)) {
            cir.setReturnValue(Shapes.block());
        }
    }
}
