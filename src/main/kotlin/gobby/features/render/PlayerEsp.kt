package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.ColorSetting
import gobby.gui.click.SelectorSetting
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.awt.Color

object PlayerEsp : ColoredEntityHighlighter(
    "Player ESP", "Renders players through walls", Category.RENDER,
    Color(0, 170, 255, 72), "player", "players"
) {

    private const val REAL_PLAYER_UUID_VERSION = 4

    val renderMode by SelectorSetting("Render Mode", 0, listOf("3D Box", "Filled 3D Box", "Model"), desc = "How to draw highlighted players")

    override fun espStyle(): EspStyle = when (renderMode) {
        1 -> EspStyle.FILLED_BOX
        2 -> EspStyle.MODEL
        else -> EspStyle.BOX
    }

    override fun shouldHighlight(entity: Entity): Boolean {
        if (entity !is Player || entity === mc.player) return false
        if (entity.isInvisible || entity.isRemoved) return false
        return entity.uuid.version() == REAL_PLAYER_UUID_VERSION
    }





}
