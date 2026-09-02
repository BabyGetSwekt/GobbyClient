package gobby.features.dungeons

import gobby.events.ChatReceivedEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.LocationUtils.masterMode
import gobby.utils.PlayerUtils
import gobby.utils.Utils.equalsOneOf
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonClass
import gobby.utils.skyblock.dungeon.DungeonUtils.myDungeonClass

object AutoUlt : Module(
    "Auto Ult", "When playing tank or healer this module automatically pops your ult",
    Category.DUNGEONS
) {

    private val onlyMasterMode by BooleanSetting("Only in master mode", false, desc = "Only automatically use ult in master mode floors")

    private val ULT_MESSAGES = setOf(
        "⚠ Maxor is enraged! ⚠",
        "[BOSS] Goldor: You have done it, you destroyed the factory…",
        "[BOSS] Sadan: My giants! Unleashed!",
        "[BOSS] Livid: I respect you for making it to here, but I'll be your undoing."
    )

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!enabled || !inDungeons || !inBoss) return
        if (onlyMasterMode && !masterMode) return
        if (!myDungeonClass.equalsOneOf(DungeonClass.Tank, DungeonClass.Healer)) return
        if (event.message !in ULT_MESSAGES) return
        PlayerUtils.dropItem()
    }
}
