package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonClass
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonTeammate
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import java.awt.Color

internal data class LeapOverlayButton(
    val targetName: String,
    val teammate: DungeonTeammate,
    val headStack: ItemStack,
    var x: Int = 0,
    var y: Int = 0,
    var width: Int = 0,
    var height: Int = 0
)

internal object LeapOverlayRenderer {
    private const val CARD_WIDTH = 160
    private const val CARD_HEIGHT = 55
    private const val GRID_GAP = 10
    private const val COLS = 2
    private const val HEAD_SCALE = 2f
    private const val HEAD_RENDER_SIZE = 32

    private val classColors = mapOf(
        DungeonClass.Archer to Color(255, 85, 85), DungeonClass.Berserk to Color(255, 170, 0),
        DungeonClass.Mage to Color(85, 85, 255), DungeonClass.Tank to Color(85, 255, 85),
        DungeonClass.Healer to Color(170, 0, 170), DungeonClass.Unknown to Color(255, 255, 85)
    )

    private val backgroundColors = mapOf(
        DungeonClass.Archer to Color(60, 15, 15, 200), DungeonClass.Berserk to Color(60, 35, 0, 200),
        DungeonClass.Mage to Color(15, 15, 60, 200), DungeonClass.Tank to Color(15, 50, 15, 200),
        DungeonClass.Healer to Color(40, 0, 40, 200), DungeonClass.Unknown to Color(50, 50, 15, 200)
    )

    private val hoverColors = mapOf(
        DungeonClass.Archer to Color(80, 25, 25, 220), DungeonClass.Berserk to Color(80, 50, 10, 220),
        DungeonClass.Mage to Color(25, 25, 80, 220), DungeonClass.Tank to Color(25, 70, 25, 220),
        DungeonClass.Healer to Color(60, 10, 60, 220), DungeonClass.Unknown to Color(70, 70, 25, 220)
    )

    fun draw(
        context: GuiGraphicsExtractor,
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        buttons: List<LeapOverlayButton>
    ): LeapOverlayButton? {
        val rows = (buttons.size + COLS - 1) / COLS
        val gridWidth = COLS * CARD_WIDTH + (COLS - 1) * GRID_GAP
        val gridHeight = rows * CARD_HEIGHT + (rows - 1) * GRID_GAP
        val startX = (width - gridWidth) / 2
        val startY = (height - gridHeight) / 2
        var hovered: LeapOverlayButton? = null
        buttons.forEachIndexed { index, button ->
            val x = startX + index % COLS * (CARD_WIDTH + GRID_GAP)
            val y = startY + index / COLS * (CARD_HEIGHT + GRID_GAP)
            button.x = x
            button.y = y
            button.width = CARD_WIDTH
            button.height = CARD_HEIGHT
            val isHovered = mouseX in x..x + CARD_WIDTH && mouseY in y..y + CARD_HEIGHT
            if (isHovered) hovered = button
            drawCard(context, button, x, y, isHovered)
        }
        return hovered
    }

    private fun drawCard(context: GuiGraphicsExtractor, button: LeapOverlayButton, x: Int, y: Int, hovered: Boolean) {
        val dungeonClass = button.teammate.dungeonClass
        val background = (if (hovered) hoverColors else backgroundColors)[dungeonClass] ?: Color.DARK_GRAY
        val accent = classColors[dungeonClass] ?: Color.WHITE
        context.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, background.rgb)
        context.fill(x, y, x + 3, y + CARD_HEIGHT, accent.rgb)
        if (hovered) {
            context.fill(x, y, x + CARD_WIDTH, y + 1, accent.rgb)
            context.fill(x, y + CARD_HEIGHT - 1, x + CARD_WIDTH, y + CARD_HEIGHT, accent.rgb)
            context.fill(x + CARD_WIDTH - 1, y, x + CARD_WIDTH, y + CARD_HEIGHT, accent.rgb)
        }
        drawHead(context, button.headStack, x + 8, y + (CARD_HEIGHT - HEAD_RENDER_SIZE) / 2)
        val textX = x + 46
        context.text(mc.font, button.teammate.name, textX, y + 14, accent.rgb, true)
        context.text(mc.font, "${dungeonClass.name} ${button.teammate.classLevel}", textX, y + 28, Color(150, 150, 160).rgb, true)
    }

    private fun drawHead(context: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        context.pose().pushMatrix()
        context.pose().translate(x.toFloat(), y.toFloat())
        context.pose().scale(HEAD_SCALE, HEAD_SCALE)
        context.item(stack, 0, 0)
        context.pose().popMatrix()
    }
}
