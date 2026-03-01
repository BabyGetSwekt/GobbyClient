package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.LeftClickEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.SelectorSetting
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.doorOpener

object AutoLeap : Module("Auto Leap", "Automatically leaps to the door opener", Category.DUNGEONS) {

    val mode by SelectorSetting("Mode", 0, listOf("Auto Leap", "Left Click"), desc = "Auto Leap: Automatically leaps\nLeft Click: Only leaps when you left click")

    private val witherDoorRegex = Regex("""^\w+ opened a WITHER door!$""")

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!inDungeons || mode != 0) return
        if (!witherDoorRegex.matches(event.message)) return

        val opener = doorOpener
        if (opener.isEmpty() || opener == mc.player?.name?.string) return

        DungeonUtils.leapTo(opener, autoSwap = true)
    }

    @SubscribeEvent
    fun onLeftClick(event: LeftClickEvent) {
        if (!inDungeons || mode != 1) return

        val opener = doorOpener
        if (opener.isEmpty() || opener == mc.player?.name?.string) return

        DungeonUtils.leapTo(opener, autoSwap = false)
    }
}
