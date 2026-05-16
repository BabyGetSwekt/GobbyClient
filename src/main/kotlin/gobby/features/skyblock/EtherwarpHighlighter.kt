package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.features.dungeons.EtherwarpTriggerbot
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.isEtherwarpable
import net.minecraft.world.phys.AABB
import java.awt.Color

object EtherwarpHighlighter {

    private val validColor = Color(0, 255, 0, 80)
    private val invalidColor = Color(255, 0, 0, 80)

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!(EtherwarpTriggerbot.enabled && EtherwarpTriggerbot.highlighter)) return
        val player = mc.player ?: return
        if (!player.isShiftKeyDown) return
        if (!player.mainHandItem.isEtherwarpable()) return

        val etherPos = EtherwarpUtils.getEtherPos()
        val pos = etherPos.pos ?: return
        val color = if (etherPos.succeeded) validColor else invalidColor

        val box = AABB(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + 1.0, pos.z + 1.0
        )
        draw3DBox(event.matrixStack, event.camera, box, color)
    }
}
