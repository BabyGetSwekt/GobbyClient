package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.KeyPressGuiEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.utils.LocationUtils
import gobby.utils.managers.WardrobeManager
import gobby.utils.render.TitleUtils
import java.awt.Color

object WardrobeSwapper : Module(
    "Auto Wardrobe", "Instantly equip wardrobe slots with keybinds",
    Category.SKYBLOCK
) {
    val slot1 by KeybindSetting("Wardrobe 1", desc = "Keybind for wardrobe slot 1")
    val slot2 by KeybindSetting("Wardrobe 2", desc = "Keybind for wardrobe slot 2")
    val slot3 by KeybindSetting("Wardrobe 3", desc = "Keybind for wardrobe slot 3")
    val slot4 by KeybindSetting("Wardrobe 4", desc = "Keybind for wardrobe slot 4")
    val slot5 by KeybindSetting("Wardrobe 5", desc = "Keybind for wardrobe slot 5")
    val slot6 by KeybindSetting("Wardrobe 6", desc = "Keybind for wardrobe slot 6")
    val slot7 by KeybindSetting("Wardrobe 7", desc = "Keybind for wardrobe slot 7")
    val slot8 by KeybindSetting("Wardrobe 8", desc = "Keybind for wardrobe slot 8")
    val slot9 by KeybindSetting("Wardrobe 9", desc = "Keybind for wardrobe slot 9")

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (!enabled) return
        if (!LocationUtils.onSkyblock) return
        if (mc.currentScreen != null) return
        val key = event.key
        if (key == 0) return

        val slot = when (key) {
            slot1 -> 1
            slot2 -> 2
            slot3 -> 3
            slot4 -> 4
            slot5 -> 5
            slot6 -> 6
            slot7 -> 7
            slot8 -> 8
            slot9 -> 9
            else -> return
        }
        WardrobeManager.swap(slot)
        TitleUtils.displayTitleTicks("Equipping WD: $slot", 20, Color(170, 0, 170))
    }
}
