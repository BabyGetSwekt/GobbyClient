package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.ColorSetting
import gobby.gui.click.SelectorSetting
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.awt.Color

object PlayerEsp : EntityHighlighter("Player ESP", "Renders players through walls", Category.RENDER) {

    private const val REAL_PLAYER_UUID_VERSION = 4

    val renderMode by SelectorSetting("Render Mode", 0, listOf("3D Box", "Filled 3D Box", "Model"), desc = "How to draw highlighted players")
    val espColor by ColorSetting("ESP Color", Color(0, 170, 255, 72), desc = "Pick a color for player highlights")
    val espLines by BooleanSetting("ESP Line", false, desc = "Draws a line to players")
    val espLineMode by SelectorSetting("Line Mode", 1, listOf("Feet", "Crosshair"), desc = "Where the line starts from")
        .withDependency { espLines }

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

    override fun getColor(): Color = espColor
    override fun shouldDrawLines(): Boolean = espLines
    override fun getLineColor(): Color = espColor
    override fun getLineMode(): Int = espLineMode
    override fun rendersArmor(): Boolean = true
}
