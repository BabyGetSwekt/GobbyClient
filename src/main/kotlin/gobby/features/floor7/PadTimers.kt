package gobby.features.floor7

import gobby.events.ChatReceivedEvent
import gobby.events.ServerTickEvent
import gobby.events.WorldUnloadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.ClickGUITheme
import gobby.gui.click.Module
import gobby.utils.Utils.equalsOneOf
import gobby.utils.render.Interpolate.interpolateColorC
import gobby.utils.render.TitleUtils
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonPad
import net.minecraft.text.Text
import java.awt.Color

object PadTimers : Module(
    "Pad Timers", "Reminds you when to step on the pads. This is based on the position that you're at.",
    Category.FLOOR7
) {

    private val COL_PURPLE = Color(170, 0, 170)
    private val COL_GREEN = Color(85, 255, 85)
    private val COL_TIMER_HIGH = Color(85, 255, 85)
    private val COL_TIMER_LOW = Color(170, 0, 0)

    private const val PURPLE_CRUSH = 96
    private const val GREEN_CRUSH = 171  // purple + 75 ticks

    private const val STORM_MSG_1 = "[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!"
    private const val STORM_MSG_2 = "[BOSS] Storm: ENERGY HEED MY CALL!"

    private var ticks = 0
    private var active = false

    private fun styledColored(s: String, color: Color): Text {
        val argb = (0xFF shl 24) or (color.red shl 16) or (color.green shl 8) or color.blue
        return Text.literal(s).setStyle(ClickGUITheme.FONT_STYLE.withColor(argb))
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!enabled) return
        if (!event.message.equalsOneOf(STORM_MSG_1, STORM_MSG_2)) return
        ticks = 0
        active = true
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent) {
        if (!active) return
        ticks++
        if (ticks > GREEN_CRUSH) { active = false; return }

        val (crushAt, name, padColor) = when (DungeonUtils.getCurrentPad()) {
            DungeonPad.Purple -> Triple(PURPLE_CRUSH, "Purple", COL_PURPLE)
            DungeonPad.Green -> Triple(GREEN_CRUSH, "Green", COL_GREEN)
            else -> return
        }

        val remaining = crushAt - ticks
        if (remaining < 0) return

        val text = if (remaining == 0) {
            Text.empty()
                .append(styledColored("Crush ", Color.WHITE))
                .append(styledColored(name, padColor))
                .append(styledColored(" NOW!", Color.WHITE))
        } else {
            val ratio = remaining.toFloat() / crushAt
            Text.empty()
                .append(styledColored("Crush ", Color.WHITE))
                .append(styledColored(name, padColor))
                .append(styledColored(" in ", Color.WHITE))
                .append(styledColored("$remaining ticks", interpolateColorC(COL_TIMER_LOW, COL_TIMER_HIGH, ratio)))
        }

        TitleUtils.displayStyledTextServerTicks(text, if (remaining == 0) 20 else 2)
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldUnloadEvent) {
        active = false
        ticks = 0
    }
}
