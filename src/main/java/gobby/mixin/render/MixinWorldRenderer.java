package gobby.mixin.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import gobby.Gobbyclient;
import gobby.events.render.NewRender3DEvent;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldRenderer.class)
public class MixinWorldRenderer {

    @Inject(at = @At("TAIL"), method = "render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V")
    public void gobbyclient$render(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline,
                       Camera camera, Matrix4f positionMatrix, Matrix4f matrix4f, Matrix4f projectionMatrix,
                       GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky,
                       CallbackInfo ci) {

        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        RenderSystem.getModelViewStack().pushMatrix().mul(positionMatrix);
        Frustum frustum = new Frustum(positionMatrix, projectionMatrix);
        frustum.setPosition(camera.getPos().x, camera.getPos().y, camera.getPos().z);

        MatrixStack matrixStack = new MatrixStack();
        NewRender3DEvent renderEvent = new NewRender3DEvent(matrixStack, frustum, tickCounter, camera);
        Gobbyclient.EVENT_MANAGER.publish(renderEvent);

        RenderSystem.getModelViewStack().popMatrix();

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }
}
