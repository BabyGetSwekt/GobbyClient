package gobby.features.floor7.terminals

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.managers.AuraManager
import gobby.utils.skyblock.dungeon.TerminalUtils
import gobby.utils.timer.Clock
import net.minecraft.world.entity.decoration.ArmorStand

object TerminalAura {

    private val clock = Clock()

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (TerminalUtils.isGuardFailed()) return
        if (!AutoTerminals.auraEnabled) return
        if (mc.gui.screen() != null) return
        if (!clock.hasTimePassed(AutoTerminals.auraDelay.toLong())) return

        val player = mc.player ?: return
        if (AutoTerminals.auraOnlyGround && !player.onGround()) return
        val world = mc.level ?: return
        val distSq = (AutoTerminals.auraDistance * AutoTerminals.auraDistance).toDouble()

        val target = world.entitiesForRendering()
            .filterIsInstance<ArmorStand>()
            .firstOrNull {
                it.customName?.string == "Inactive Terminal" &&
                    player.distanceToSqr(it) <= distSq
            } ?: return

        AuraManager.auraEntity(target)
        clock.update()
    }
}
