package gobby.events.render


import gobby.events.Events
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;


class NewRender3DEvent(
    val matrixStack: MatrixStack,
    val frustum: Frustum,
    val renderTickCounter: RenderTickCounter,
    val camera: Camera,
) : Events()
