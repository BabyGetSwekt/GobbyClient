package gobby.mixin.render;

import gobby.features.skyblock.FreeCam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void gobbyclient$freeCamCrosshair(float tickDelta, CallbackInfo ci) {
        if (!FreeCam.INSTANCE.getEnabled()) return;
        if (this.minecraft.level == null || this.minecraft.player == null) return;

        double reach = this.minecraft.player.blockInteractionRange();

        Vec3 start = new Vec3(
                FreeCam.INSTANCE.getCamX(),
                FreeCam.INSTANCE.getCamY(),
                FreeCam.INSTANCE.getCamZ()
        );

        float yaw = FreeCam.INSTANCE.getCamYaw();
        float pitch = FreeCam.INSTANCE.getCamPitch();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double lookY = -Math.sin(pitchRad);
        double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);

        Vec3 end = start.add(lookX * reach, lookY * reach, lookZ * reach);

        BlockHitResult blockHit = this.minecraft.level.clip(new ClipContext(
                start, end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                this.minecraft.player
        ));

        this.minecraft.hitResult = blockHit;
    }
}
