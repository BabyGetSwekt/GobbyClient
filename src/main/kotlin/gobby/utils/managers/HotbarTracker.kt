package gobby.utils.managers

import gobby.Gobbyclient
import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.HotbarUpdateEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.skyblockID

object HotbarTracker {

    private val cachedIds = Array(9) { "" }
    private val cachedCounts = IntArray(9)

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        val player = mc.player ?: return
        for (i in 0..8) {
            val stack = player.inventory.getStack(i)
            val id = stack.skyblockID
            val count = if (stack.isEmpty) 0 else stack.count
            if (id != cachedIds[i] || count != cachedCounts[i]) {
                Gobbyclient.EVENT_MANAGER.publish(
                    HotbarUpdateEvent(cachedIds[i], id, i, cachedCounts[i], count)
                )
                cachedIds[i] = id
                cachedCounts[i] = count
            }
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        cachedIds.fill("")
        cachedCounts.fill(0)
    }
}
