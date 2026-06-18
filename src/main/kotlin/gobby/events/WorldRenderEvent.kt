package gobby.events

import net.minecraft.client.Camera
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.DeltaTracker
import net.minecraft.client.renderer.SubmitNodeCollector
import com.mojang.blaze3d.vertex.PoseStack

class WorldRenderEvent(
    val matrices: PoseStack,
    val tickCounter: DeltaTracker,
    val frustum: Frustum,
    val camera: Camera,
    val gameRenderer: GameRenderer,
    val vertexConsumers: SubmitNodeCollector
) : Events()