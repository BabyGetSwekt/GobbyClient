package gobby.features.floor7.terminals

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.screens.inventory.ContainerScreen

abstract class TerminalSolver {

    protected var active = false

    abstract val isEnabled: Boolean
    abstract fun matchesTitle(title: String): Boolean
    abstract fun solve(screen: ContainerScreen): TerminalClick?

    open fun onActivate(screen: ContainerScreen) {}
    open fun onDeactivate() {}

    fun isActive(): Boolean = active

    protected fun tickScreen(): ContainerScreen? {
        if (TerminalUtils.isGuardFailed() || !isEnabled) return null

        val screen = (mc.screen as? ContainerScreen)
            ?.takeIf { matchesTitle(it.title.string) }

        if (screen == null) {
            if (active) {
                active = false
                onDeactivate()
            }
            return null
        }

        if (!active) {
            active = true
            TerminalUtils.onTerminalOpen(screen)
            onActivate(screen)
        }

        return screen
    }

    @SubscribeEvent
    open fun onTick(event: ClientTickEvent.Post) {
        val screen = tickScreen() ?: return
        val click = solve(screen) ?: return
        TerminalUtils.tryClick(screen, click.slot, click.button)
    }
}

data class TerminalClick(val slot: Int, val button: Int = 2)
