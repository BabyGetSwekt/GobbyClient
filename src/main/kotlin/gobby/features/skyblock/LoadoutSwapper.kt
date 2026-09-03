package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.KeyPressGuiEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.utils.LocationUtils
import gobby.utils.managers.LoadoutManager
import gobby.utils.render.TitleUtils
import java.awt.Color

object LoadoutSwapper : Module(
    "Loadout Swapper", "Instantly equip loadout slots with keybinds", Category.SKYBLOCK
) {
    private val worksOutsideLoadoutMenu by BooleanSetting(
        "Works outside Loadout menu", false,
        desc = "Allows loadout keybinds to work outside the Loadout menu"
    )
    private val slots = listOf(
        KeybindSetting("Loadout 1", desc = "Keybind for loadout slot 1"),
        KeybindSetting("Loadout 2", desc = "Keybind for loadout slot 2"),
        KeybindSetting("Loadout 3", desc = "Keybind for loadout slot 3"),
        KeybindSetting("Loadout 4", desc = "Keybind for loadout slot 4"),
        KeybindSetting("Loadout 5", desc = "Keybind for loadout slot 5"),
        KeybindSetting("Loadout 6", desc = "Keybind for loadout slot 6"),
        KeybindSetting("Loadout 7", desc = "Keybind for loadout slot 7"),
        KeybindSetting("Loadout 8", desc = "Keybind for loadout slot 8"),
        KeybindSetting("Loadout 9", desc = "Keybind for loadout slot 9"),
        KeybindSetting("Loadout 10", desc = "Keybind for loadout slot 10"),
        KeybindSetting("Loadout 11", desc = "Keybind for loadout slot 11"),
        KeybindSetting("Loadout 12", desc = "Keybind for loadout slot 12")
    )

    init { slots.forEach(settings::add) }

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (!enabled || !LocationUtils.onSkyblock || LoadoutManager.isSwapping) return
        val loadoutOpen = LoadoutManager.isLoadoutScreenOpen
        if (!worksOutsideLoadoutMenu && !loadoutOpen) return
        if (worksOutsideLoadoutMenu && mc.gui.screen() != null && !loadoutOpen) return
        val slot = slots.indexOfFirst { it.value == event.key }
        if (slot < 0) return
        LoadoutManager.swap(slot + 1)
        TitleUtils.displayStyledTitleTicks("Equipping Loadout: ${slot + 1}", 20, Color(170, 0, 170))
    }
}
