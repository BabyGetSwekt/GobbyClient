package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.events.ChatReceivedEvent
import gobby.events.LeftClickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.doorOpener

object AutoLeap {

    private val witherDoorRegex = Regex("""^\w+ opened a WITHER door!$""")

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!inDungeons || GobbyConfig.autoLeapMode != 1) return
        if (!witherDoorRegex.matches(event.message)) return

        val opener = doorOpener
        if (opener.isEmpty() || opener == mc.player?.name?.string) return

        DungeonUtils.leapTo(opener, autoSwap = true)
    }

    @SubscribeEvent
    fun onLeftClick(event: LeftClickEvent) {
        if (!inDungeons || GobbyConfig.autoLeapMode != 2) return

        val opener = doorOpener
        if (opener.isEmpty() || opener == mc.player?.name?.string) return

        DungeonUtils.leapTo(opener, autoSwap = false)
    }
}
