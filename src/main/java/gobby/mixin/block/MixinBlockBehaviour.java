package gobby.mixin.block;

import gobby.features.dungeons.SecretHitbox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public class MixinBlockBehaviour {

    @Inject(method = "getCollisionShape", at = @At("HEAD"))
    private void beforeGetCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (state.getBlock() instanceof AbstractSkullBlock) {
            SecretHitbox.inCollisionCheck = true;
        }
    }

    @Inject(method = "getCollisionShape", at = @At("RETURN"))
    private void afterGetCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        SecretHitbox.inCollisionCheck = false;
    }
}
