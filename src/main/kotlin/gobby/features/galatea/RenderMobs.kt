package gobby.features.galatea

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.utils.render.BlockRenderUtils.drawLine3D
import gobby.utils.render.Render3D.drawEntityModel
import gobby.utils.render.Interpolate
import net.minecraft.entity.passive.TurtleEntity
import net.minecraft.util.math.Vec3d

object RenderMobs {

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (mc.player == null || mc.world == null || !GobbyConfig.turtleEsp) return
        val player = mc.player ?: return
        val matrixStack = event.matrixStack
        val camera = mc.gameRenderer.camera
        val delta = event.renderTickCounter.getTickProgress(false)

        for (entity in mc.world!!.entities) {
            if (entity is TurtleEntity) {
                event.drawEntityModel(
                    matrixStack,
                    camera,
                    delta,
                    entity,
                    GobbyConfig.turtleEspColor
                )
                if (GobbyConfig.turtleEspLines) {
                    val playerInterpolated = if (GobbyConfig.turtleEspLineMode == 0) {
                        Interpolate.interpolateEntity(player)
                    } else {
                        Interpolate.interpolatedLookVec()
                    }
                    val turtleInterpolated = Interpolate.interpolateEntity(entity)
                    drawLine3D(
                        matrixStack,
                        camera,
                        playerInterpolated,
                        turtleInterpolated,
                        GobbyConfig.turtleEspLineColor
                    )
                }
            }
        }
    }
}