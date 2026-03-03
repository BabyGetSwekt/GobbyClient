package gobby.mixin;

import gobby.features.dungeons.SecretHitbox;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.WallSkullBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WallSkullBlock.class)
public class MixinWallSkullBlock {

    @Inject(method = "getOutlineShape", at = @At("HEAD"), cancellable = true)
    private void onGetOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!SecretHitbox.INSTANCE.getEnabled()) return;
        if (!SecretHitbox.inCollisionCheck && SecretHitbox.INSTANCE.isSecretSkull(world, pos)) {
            cir.setReturnValue(VoxelShapes.fullCube());
        }
    }
}
