package gobby.features.floor7.terminals

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.managers.AuraManager
import gobby.utils.skyblock.dungeon.TerminalUtils
import gobby.utils.timer.Clock
import net.minecraft.entity.decoration.ArmorStandEntity

object TerminalAura {

    private val clock = Clock()

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (TerminalUtils.isGuardFailed()) return
        if (!AutoTerminals.auraEnabled) return
        if (mc.currentScreen != null) return
        if (!clock.hasTimePassed(AutoTerminals.auraDelay.toLong())) return

        val player = mc.player ?: return
        if (AutoTerminals.auraOnlyGround && !player.isOnGround) return
        val world = mc.world ?: return
        val distSq = (AutoTerminals.auraDistance * AutoTerminals.auraDistance).toDouble()

        val target = world.entities
            .filterIsInstance<ArmorStandEntity>()
            .firstOrNull {
                it.customName?.string == "Inactive Terminal" &&
                    player.squaredDistanceTo(it) <= distSq
            } ?: return

        AuraManager.auraEntity(target)
        clock.update()
    }
}
