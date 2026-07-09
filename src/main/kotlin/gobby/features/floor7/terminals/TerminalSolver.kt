package gobby.features.floor7.terminals

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.screens.inventory.ContainerScreen

private const val STUCK_LOG_TICKS = 30

abstract class TerminalSolver {

    protected var active = false
    private var emptyTicks = 0
    private var stuckLogged = false

    abstract val isEnabled: Boolean
    abstract fun matchesTitle(title: String): Boolean
    abstract fun solve(screen: ContainerScreen): TerminalClick?

    open fun onActivate(screen: ContainerScreen) {}
    open fun onDeactivate() {}

    open fun onStuck(screen: ContainerScreen) {}

    fun isActive(): Boolean = active

    private fun resetStuck() {
        emptyTicks = 0
        stuckLogged = false
    }

    protected fun tickScreen(): ContainerScreen? {
        if (TerminalUtils.isGuardFailed() || !isEnabled) return null

        val screen = (mc.gui.screen() as? ContainerScreen)
            ?.takeIf { matchesTitle(it.title.string) }

        if (screen == null) {
            if (active) {
                active = false
                resetStuck()
                onDeactivate()
            }
            return null
        }

        if (!active) {
            active = true
            resetStuck()
            TerminalUtils.onTerminalOpen(screen)
            onActivate(screen)
        }

        return screen
    }

    @SubscribeEvent
    open fun onTick(event: ClientTickEvent.Post) {
        val screen = tickScreen() ?: return
        val click = solve(screen)
        if (click == null) {
            if (++emptyTicks >= STUCK_LOG_TICKS && !stuckLogged) {
                stuckLogged = true
                onStuck(screen)
            }
            return
        }
        resetStuck()
        TerminalUtils.tryClick(screen, click.slot, click.button)
    }
}

data class TerminalClick(val slot: Int, val button: Int = 2)
