package gobby.features.floor7

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.events.ClientTickEvent
import gobby.events.MouseButtonEvent
import gobby.events.ServerTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.PlayerUtils.isPlayerInBox
import gobby.utils.Utils.equalsOneOf
import gobby.utils.skyblockID
import gobby.utils.timer.Executor

object LastBreathHelper {

    private var ticks = 0
    private var lbCharged = false
    private var rcButtonState = false

    private fun getMaxTicks(): Int {
        return when {
            isPlayerInBox(47, 8, 113, 64, 28, 135) -> 6   // Purple
            isPlayerInBox(13, 5, 85, 40, 27, 103) -> 8    // Green
            isPlayerInBox(13, 4, 47, 40, 20, 68) -> 8     // Red
            isPlayerInBox(72, 3, 47, 97, 31, 65) -> 8     // Orange
            isPlayerInBox(72, 3, 85, 97, 31, 107) -> 8    // Blue
            else -> 0
        }
    }

    @SubscribeEvent
    fun onMouseButton(event: MouseButtonEvent) {
        if (mc.player == null || mc.world == null) return
        if (dungeonFloor != 7 || !inBoss) return
        if (!GobbyConfig.p5DebuffHelper || !GobbyConfig.lastBreathHelper || getMaxTicks() == 0) return
        if (event.button != MouseButtonEvent.RIGHT_BUTTON) return
        if (mc.currentScreen != null) return

        rcButtonState = event.action == MouseButtonEvent.PRESS
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent) {
        if (mc.player == null || mc.world == null) return
        if (dungeonFloor != 7 || !inBoss) return
        if (!GobbyConfig.p5DebuffHelper || !GobbyConfig.lastBreathHelper || getMaxTicks() == 0) return

        if (!mc?.player?.mainHandStack?.skyblockID.equalsOneOf("LAST_BREATH", "STARRED_LAST_BREATH")) {
            ticks = 0
            return
        }

        if (lbCharged && rcButtonState) {
            if (ticks < getMaxTicks()) ticks++

            if (ticks == getMaxTicks()) {
                Executor.execute(1) { mc.options.useKey.isPressed = false }

                Executor.execute(3) { mc.options.useKey.isPressed = rcButtonState }
            }
        }
    }

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Pre) {
        if (mc.player == null || mc.world == null) return
        if (dungeonFloor != 7 || !inBoss) return
        if (!GobbyConfig.p5DebuffHelper || !GobbyConfig.lastBreathHelper || getMaxTicks() == 0) return
        if (mc.options.useKey.isPressed) {
            lbCharged = true
        } else {
            lbCharged = false
            ticks = 0
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        ticks = 0
        lbCharged = false
    }
}
