package gobby.mixin.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import gobby.Gobbyclient;
import gobby.events.render.NewRender3DEvent;
import gobby.mixin.accessor.CameraAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class)
public class MixinLevelRenderer {

    @Inject(at = @At("TAIL"), method = "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V")
    public void gobbyclient$render(GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline,
                       Camera camera, Matrix4f positionMatrix, Matrix4f matrix4f, Matrix4f projectionMatrix,
                       GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky,
                       CallbackInfo ci) {

        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        RenderSystem.getModelViewStack().pushMatrix().mul(positionMatrix);
        Frustum frustum = new Frustum(positionMatrix, projectionMatrix);
        //? if <=1.21.10
        frustum.prepare(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
        //? if >=1.21.11
        /*frustum.prepare(camera.position().x, camera.position().y, camera.position().z);*/

        PoseStack matrixStack = new PoseStack();
        NewRender3DEvent renderEvent = new NewRender3DEvent(matrixStack, frustum, tickCounter, camera);
        Gobbyclient.EVENT_MANAGER.publish(renderEvent);

        RenderSystem.getModelViewStack().popMatrix();

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }
}
