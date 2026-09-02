package gobby.features.floor7

import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.masterMode
import gobby.utils.getHelmetID
import gobby.utils.managers.EquipmentManager
import gobby.utils.managers.WardrobeManager
import gobby.utils.render.TitleUtils
import java.awt.Color

object CowHatHelper : Module(
    "Cow Hat Helper", "Helper that reminds you to wear/remove cow hat on P5, also has an option to automatically do it for you",
    Category.FLOOR7
) {

    private val autoSwapCow by BooleanSetting("Auto Swap to Cow", false, desc = "Automatically swap to cow hat on P4 end and automatically swaps back to your old hat after Relics are done")

    private enum class Reminder(val wantsCow: Boolean) { WEAR(true), REMOVE(false) }

    private var reminder: Reminder? = null
    private var previousHelmet = ""

    private val inMaster7: Boolean get() = enabled && masterMode && dungeonFloor == 7

    private val wearingCow: Boolean get() = getHelmetID() == "COW_HEAD"

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!inMaster7) return
        when (event.message) {
            "[BOSS] Necron: All this, for nothing..." -> startWearing()
            "[BOSS] Wither King: You... again?" -> startRemoving()
        }
    }

    private fun startWearing() {
        if (wearingCow) return
        previousHelmet = WardrobeManager.wornHelmet
        reminder = Reminder.WEAR
        if (!autoSwapCow) return TitleUtils.displayStyledTitleTicks("Cow Hat Reminder!", 60, Color.WHITE)
        EquipmentManager.swapHead("COW_HEAD")
        TitleUtils.displayStyledTitleTicks("Autoswapping to Cow", 40, Color.WHITE)
    }

    private fun startRemoving() {
        if (!wearingCow) return
        reminder = Reminder.REMOVE
        if (!autoSwapCow || previousHelmet.isEmpty()) return TitleUtils.displayStyledTitleTicks("Remove Cow Hat!", 200, Color.RED)
        EquipmentManager.swapHeadByUuid(previousHelmet)
        TitleUtils.displayStyledTitleTicks("Autoswapping back", 40, Color.WHITE)
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        val active = reminder ?: return
        if (!inMaster7) return
        if (wearingCow != active.wantsCow) return
        TitleUtils.hide()
        reminder = null
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldLoadEvent) {
        reminder = null
        previousHelmet = ""
    }
}
