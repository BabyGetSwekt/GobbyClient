package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.DungeonRunEndEvent
import gobby.events.PartyEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.SettingAlign
import gobby.gui.click.SettingSection
import gobby.gui.click.inGroup
import gobby.gui.click.styledText
import gobby.gui.hud.HudSetting
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.ChatUtils.sendCommand
import gobby.utils.ContainerClicks
import gobby.utils.getLoreStrings
import gobby.utils.managers.PartyManager
import gobby.utils.render.Interpolate.interpolateColorC
import gobby.utils.timer.Cooldown
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import java.awt.Color

object AutoRequeue : Module(
    "Auto Requeue", "Requeues into a new dungeon once the run is over, has cooldown timer aswell",
    Category.DUNGEONS
) {

    private const val WARP_COOLDOWN_SECONDS = 30
    private val SOLO_CLEAR_SECTION = SettingSection("Solo clear", SettingAlign.RIGHT)

    private val soloClearRequeue by BooleanSetting("Solo Clear Auto Requeue", false, desc = "Auto confirms confirmation menu")
        .inGroup(SOLO_CLEAR_SECTION)

    private val ENTERED_DUNGEON = Regex(
        """^-*\n(?:\[[^]]+] )?(\w{1,16}) entered (MM )?The Catacombs, (?:Entrance|Floor (I|II|III|IV|V|VI|VII))!\n-*$"""
    )

    private val warpCooldown = Cooldown()
    private var partyLeftDuringRun = false
    private var confirmTicks = 0
    private var confirmedContainerId = -1

    private val warpHud by HudSetting("Warp Cooldown", "Time until you can create a new dungeon", visible = { warpCooldown.isActive }) { example ->
        val ctx = drawContext ?: return@HudSetting
        val seconds = if (example) WARP_COOLDOWN_SECONDS.toDouble() else warpCooldown.remainingSeconds
        if (seconds <= 0.0) return@HudSetting
        val text = styledText("Warp Cooldown: %.2fs".format(seconds))
        ctx.text(mc.font, text, 0, 0, colorFor(seconds), true)
        setSize(mc.font.width(text), mc.font.lineHeight)
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!ENTERED_DUNGEON.matches(event.message)) return
        warpCooldown.start(WARP_COOLDOWN_SECONDS)
        partyLeftDuringRun = false
    }

    @SubscribeEvent
    fun onPartyLeave(event: PartyEvent.Leave) {
        partyLeftDuringRun = true
    }

    @SubscribeEvent
    fun onRunEnd(event: DungeonRunEndEvent) {
        if (!enabled) return
        if (!PartyManager.isLeader || partyLeftDuringRun) return
        sendCommand("instancerequeue")
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        val screen = (mc.gui.screen() as? AbstractContainerScreen<*>)
            ?.takeIf { enabled && soloClearRequeue && isUndersizedSolo(it) }
        if (screen == null) {
            confirmTicks = 0
            confirmedContainerId = -1
            return
        }
        if (screen.menu.containerId == confirmedContainerId) return
        if (++confirmTicks < 10) return
        ContainerClicks.pickup(screen.menu.containerId, 13)
        confirmedContainerId = screen.menu.containerId
        confirmTicks = 0
    }

    private fun isUndersizedSolo(screen: AbstractContainerScreen<*>): Boolean {
        val stack = screen.menu.slots.getOrNull(13)?.item ?: return false
        return stack.hoverName.string.noControlCodes == "Undersized party!" &&
            stack.getLoreStrings().any { it.noControlCodes.trim() == "Your party: Solo" }
    }

    private fun colorFor(seconds: Double): Int =
        interpolateColorC(Color.RED, Color.GREEN, (seconds / WARP_COOLDOWN_SECONDS).toFloat()).rgb
}
