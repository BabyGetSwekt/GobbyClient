package gobby.features.floor7

import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.WorldUnloadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.LocationUtils
import gobby.utils.getHelmetID
import gobby.utils.managers.EquipmentManager
import gobby.utils.render.TitleUtils
import java.awt.Color

object CowHatReminder : Module(
    "Cow Hat Reminder", "Reminds you to wear/remove cow hat on F7",
    Category.FLOOR7
) {

    private const val COW_HEAD = "COW_HEAD"

    val autoSwapCow by BooleanSetting("Auto Swap to Cow", false, desc = "Automatically swap to cow hat on P4 end")
    val autoSwapBack by BooleanSetting("Auto Swap Back", false, desc = "Automatically swap back to original helmet on P5 start (for example your diamond head)").withDependency { autoSwapCow }

    private var wearReminderSent = false
    private var unwearReminderSent = false
    private var previousHelmetId = ""

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!enabled || LocationUtils.dungeonFloor != 7) return
        val currentHelmet = getHelmetID()

        when (event.message) {
            "[BOSS] Necron: All this, for nothing..." -> {
                if (currentHelmet == COW_HEAD) return
                previousHelmetId = currentHelmet
                if (autoSwapCow) {
                    EquipmentManager.swapHead(COW_HEAD)
                    TitleUtils.displayStyledTitleTicks("Autoswapping to Cow", 40, Color.WHITE)
                } else TitleUtils.displayStyledTitleTicks("Cow Hat Reminder!", 60, Color.WHITE)
                wearReminderSent = true
            }
            "[BOSS] Wither King: You... again?" -> {
                if (currentHelmet != COW_HEAD) return
                if (autoSwapBack && previousHelmetId.isNotEmpty()) EquipmentManager.swapHead(previousHelmetId)
                else TitleUtils.displayStyledTitleTicks("Remove Cow Hat!", 200, Color.RED)

                unwearReminderSent = true
            }
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!wearReminderSent && !unwearReminderSent) return
        if (!enabled || LocationUtils.dungeonFloor != 7) return
        val wearingCow = getHelmetID() == COW_HEAD

        if (wearReminderSent && wearingCow) {
            TitleUtils.hide()
            wearReminderSent = false
        }

        if (unwearReminderSent && !wearingCow) {
            TitleUtils.hide()
            unwearReminderSent = false
        }
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldUnloadEvent) {
        wearReminderSent = false
        unwearReminderSent = false
        previousHelmetId = ""
    }
}
