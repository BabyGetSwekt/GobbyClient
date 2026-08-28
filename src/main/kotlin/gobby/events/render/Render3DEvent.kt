package gobby.events.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.Events
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker

class Render3DEvent(
    val context: LevelRenderContext,
    val type: Type
) : Events() {

    enum class Type {
        BeforeEntity, AfterEntity
    }
}

val Render3DEvent.matrixStack: PoseStack get() = context.poseStack()

val Render3DEvent.camera: Camera get() = mc.gameRenderer.mainCamera()

val Render3DEvent.renderTickCounter: DeltaTracker get() = mc.deltaTracker
