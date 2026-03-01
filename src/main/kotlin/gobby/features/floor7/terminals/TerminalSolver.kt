package gobby.features.floor7.terminals

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen

abstract class TerminalSolver {

    protected var active = false

    abstract val isEnabled: Boolean
    abstract fun matchesTitle(title: String): Boolean
    abstract fun solve(screen: GenericContainerScreen): TerminalClick?

    open fun onActivate(screen: GenericContainerScreen) {}
    open fun onDeactivate() {}

    protected fun tickScreen(): GenericContainerScreen? {
        if (TerminalUtils.isGuardFailed() || !isEnabled) return null

        val screen = (mc.currentScreen as? GenericContainerScreen)
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
