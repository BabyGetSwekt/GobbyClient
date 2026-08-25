package gobby.mixin;

import gobby.utils.render.PreviewModelTint;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.zombie.AbstractZombieModel;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractZombieModel.class)
public class MixinAbstractZombieModel {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V", at = @At("TAIL"))
    private void gobbyclient$applyPreviewPose(ZombieRenderState state, CallbackInfo ci) {
        PreviewModelTint.INSTANCE.poseForPreview((HumanoidModel<?>) (Object) this);
    }
}
