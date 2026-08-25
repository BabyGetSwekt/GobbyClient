package gobby.events.render

import gobby.events.Events
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.vertex.PoseStack;

class NewRender3DEvent(
    val matrixStack: PoseStack,
    val frustum: Frustum,
    val renderTickCounter: DeltaTracker,
    val camera: Camera,
) : Events()
