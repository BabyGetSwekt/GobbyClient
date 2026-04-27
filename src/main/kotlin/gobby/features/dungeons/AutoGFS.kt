package gobby.features.dungeons

import gobby.events.ClientTickEvent
import gobby.events.HotbarUpdateEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.ChatUtils
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.countInHotbar
import gobby.utils.timer.Clock

object AutoGFS : Module("Auto GFS", "Automatically retrieves certain items from sacks", Category.DUNGEONS) {

    private val enderPearls by BooleanSetting("Ender Pearls", false, desc = "Restock ender pearls from sacks")
    private val inflatableJerry by BooleanSetting("Inflatable Jerry", false, desc = "Restock inflatable jerry from sacks")

    private data class TrackedItem(
        val id: String,
        val max: Int,
        val delayMs: Long,
        val setting: () -> Boolean,
        val clock: Clock = Clock(),
        var scheduled: Boolean = false,
        var restocking: Boolean = false
    )

    private val items = listOf(
        TrackedItem("ENDER_PEARL", 16, 3000L, { enderPearls }),
        TrackedItem("INFLATABLE_JERRY", 64, 5000L, { inflatableJerry })
    )

    @SubscribeEvent
    fun onHotbarUpdate(event: HotbarUpdateEvent) {
        if (!enabled || !inDungeons) return
        val item = items.firstOrNull { it.setting() && event.itemBefore == it.id } ?: return

        val countDecreased = event.itemAfter == item.id && event.countAfter < event.countBefore
        val countIncreased = event.countAfter > event.countBefore

        if (item.restocking && countIncreased) {
            item.restocking = false
            item.scheduled = false
            return
        }

        if (item.restocking || item.scheduled || !countDecreased) return
        item.clock.update()
        item.scheduled = true
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!enabled || !inDungeons) return
        for (item in items) {
            if (!item.scheduled || !item.clock.hasTimePassed(item.delayMs)) continue
            item.scheduled = false

            val missing = item.max - countInHotbar(item.id)
            if (missing <= 0) continue

            ChatUtils.sendCommand("gfs ${item.id} $missing")
            item.restocking = true
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        items.forEach {
            it.restocking = false
            it.scheduled = false
        }
    }
}
