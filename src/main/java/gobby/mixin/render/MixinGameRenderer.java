package gobby.mixin.render;

import gobby.features.skyblock.FreeCam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "updateCrosshairTarget", at = @At("RETURN"))
    private void gobbyclient$freeCamCrosshair(float tickDelta, CallbackInfo ci) {
        if (!FreeCam.INSTANCE.getEnabled()) return;
        if (this.client.world == null || this.client.player == null) return;

        double reach = this.client.player.getBlockInteractionRange();

        Vec3d start = new Vec3d(
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

        Vec3d end = start.add(lookX * reach, lookY * reach, lookZ * reach);

        BlockHitResult blockHit = this.client.world.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                this.client.player
        ));

        this.client.crosshairTarget = blockHit;
    }
}
